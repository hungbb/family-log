# 06 — Backend Architecture (`api`)

> Java 21 · Spring Boot 3.x · PostgreSQL 16 · Flyway · DDD layered structure.

---

## 1. Stack Decisions

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Language | **Java 21 (LTS)** | Records, sealed types, pattern matching, virtual threads. |
| Framework | **Spring Boot 3.3+** | Mandated by the draft; mature security and data ecosystem. |
| Build | **Maven** | Predictable, ubiquitous in Spring projects; single `pom.xml` per module. |
| Persistence | **Spring Data JPA / Hibernate 6** + **jOOQ-style native queries where needed** | JPA for aggregate CRUD; hand-written SQL for tree/closure/authorization reads. |
| Migration | **Flyway** | Mandated. Forward-only. |
| Security | **Spring Security 6** + custom JWT filter | Standard filter chain, method security for the declarative pre-check. |
| Mapping | **MapStruct** | Compile-time DTO↔domain mapping; no reflection cost, no hand-rolled boilerplate. |
| Validation | **Jakarta Bean Validation (Hibernate Validator)** | Declarative request validation at the edge. |
| API docs | **springdoc-openapi** | `/swagger-ui.html`, spec exported from the build. |
| Testing | **JUnit 5, AssertJ, Mockito, Testcontainers, RestAssured/MockMvc, ArchUnit** | See [09-engineering-conventions.md](09-engineering-conventions.md). |
| Logging | **SLF4J + Logback**, JSON encoder in non-dev | Structured logs with correlation ids. |
| Metrics | **Micrometer + Actuator** | Prometheus scrape. |
| Concurrency | **Virtual threads enabled** (`spring.threads.virtual.enabled=true`) | Blocking JDBC without thread-pool tuning pain. |

**Explicitly not used**: Lombok (Java 21 records plus IDE generation suffice, and it keeps the build free of an annotation-processor conflict with MapStruct), reactive stack (WebFlux — unnecessary for this load profile and hostile to JDBC).

## 2. Module & Package Layout

One Maven module, package-per-bounded-context, layered inside each. Boundaries are enforced by **ArchUnit tests**, not by convention alone.

```
api/
  pom.xml
  src/main/java/app/giaky/
    GiaKyApplication.java
    common/                        # cross-cutting, no business logic
      error/                       # ProblemDetail mapping, exception hierarchy
      security/                    # JwtFilter, SecurityConfig, CurrentUser resolver
      audit/                       # AuditWriter port + JPA adapter
      web/                         # pagination, ETag support, correlation filter
      types/                       # shared value objects (PartialDate, MediaRef, ids)
    identity/
      domain/                      # User, EmailAddress, PasswordHash  (no framework)
      application/                 # AuthService, AccountService, commands/results
      infrastructure/              # UserJpaEntity, repositories, BCrypt adapter
      api/                         # AuthController, MeController, DTOs
    clanorg/
      domain/                      # Clan, Branch, Membership, JoinRequest, BranchPathService
      application/                 # ClanService, BranchService, MembershipService, JoinRequestService
      infrastructure/
      api/
    lineage/
      domain/                      # Person, Union, Parentage, LineageGraphService
      application/                 # PersonService, RelationshipService, TreeQueryService
      infrastructure/              # JPA entities, AncestryProjection (native SQL)
      api/
    authorization/
      domain/                      # PermissionGrant, Scope, PermissionResolver, ports
      application/                 # GrantService, PermissionQueryService
      infrastructure/              # GrantJpaRepository, BranchHierarchyAdapter, AncestryAdapter
      api/                         # PermissionController
    linking/
      domain/                      # NodeClaimRequest, PersonUserLinker
      application/
      infrastructure/
      api/
    media/                         # MediaStorage port, local filesystem adapter, upload controller
    notification/                  # Notification entity, in-app sender, event listeners
  src/main/resources/
    application.yml, application-dev.yml, application-prod.yml
    db/migration/                  # Flyway V*.sql
    db/seed/                       # dev-only seed SQL
  src/test/java/...                # mirrors main; plus architecture/ and e2e/
```

### 2.1 Layer rules (ArchUnit-enforced)

| Rule | Enforcement |
|------|-------------|
| `domain` depends on nothing but `common.types` and the JDK. | `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..api..", "org.springframework..")` |
| `application` may use `domain` + ports; never `api`. | ArchUnit layered-architecture rule |
| `api` may use `application` + DTOs; never repositories or JPA entities. | ArchUnit |
| No context imports another context's `domain` or `infrastructure`. | ArchUnit slice rule; cross-context access goes via published application services or events |
| Controllers never inject repositories. | ArchUnit |
| Every JPA repository read method takes a `ClanId` (or is annotated `@TenantExempt` with justification). | Custom ArchUnit rule + review |

> **Pragmatic exception.** Aggregates are modelled as plain domain objects; JPA entities are separate classes in `infrastructure` with MapStruct mappers between them. This costs some mapping code but keeps the domain persistence-ignorant and its invariants unit-testable without a database — a worthwhile trade for a permission-critical system. Simple, non-invariant-bearing tables (`notifications`, `audit_entries`, `media_objects`) skip the split and use entities directly.

## 3. Request Lifecycle

```
HTTP request
  → CorrelationIdFilter        (assign / propagate X-Correlation-Id into MDC)
  → RateLimitFilter            (bucket4j, auth + global buckets)
  → JwtAuthenticationFilter    (validate access token → AuthenticatedUser principal)
  → Spring Security chain      (authenticated? route rules)
  → @RestController            (Bean Validation, DTO binding)
      → @RequiresPermission    (declarative coarse pre-check, doc 04 §5.1)
      → ApplicationService     (@Transactional, permissionResolver.require(...), orchestration)
          → DomainService / Aggregate  (invariants)
          → Repository / Port          (clan-scoped queries)
      ← domain events published after commit
  ← DTO ← MapStruct ← domain result
  ← GlobalExceptionHandler → RFC 7807 ProblemDetail
```

**Transaction boundary is the application service.** Controllers are never transactional; repositories never open transactions of their own.

## 4. Key Components

### 4.1 `PermissionResolver` (authorization/domain)

```java
public interface PermissionResolver {
    AccessDecision decide(UserId user, Permission permission, Target target);
    void require(UserId user, Permission permission, Target target);      // throws PermissionDeniedException
    EditableSet editableSet(UserId user, ClanId clan);                    // batch, for canEdit flags
}
```

- Pure domain logic; depends on `MembershipPort`, `GrantPort`, `BranchHierarchyPort`, `LineageAncestryPort`.
- Fully unit-testable with stub ports — no Spring, no database (doc 04 §8).
- `AccessDecision` carries the `authorizingGrantId` so the audit writer can record *why* an action was allowed (FR-Z8).

### 4.2 `@RequiresPermission`

A method annotation on controllers resolved by a `HandlerInterceptor`/AOP aspect that extracts `clanId` and target ids from path variables. It is a **fast fail only**; the authoritative check lives in the service. An ArchUnit test asserts every mutating application-service method calls `require(...)` or is on an explicit allow-list.

### 4.3 `TreeQueryService` (lineage/application)

- Single flat fetch per clan (persons + parentages + unions + branches) using native queries, assembled in memory.
- Applies the `EditableSet` to stamp `canEdit`.
- Caches per (clanId, treeVersion) in Caffeine; `treeVersion` is a per-clan counter bumped on any lineage mutation, which also drives the HTTP `ETag`.

### 4.4 `AncestryProjection` (lineage/infrastructure)

Maintains `person_ancestry` inside the mutation transaction (doc 03 §3.10) with three native statements: insert-cross-product on add, recursive recompute on remove, full rebuild for repair.

### 4.5 `AuditWriter` (common/audit)

Listens for `@TransactionalEventListener(AFTER_COMMIT)` domain events, serializes before/after JSONB snapshots, and appends to `audit_entries`. The application DB role has `INSERT`-only rights on that table.

### 4.6 `MediaStorage` port

```java
public interface MediaStorage {
    StoredMedia store(UploadRequest req);
    Optional<InputStream> read(String storageKey);
    void delete(String storageKey);
}
```
In v1 the only adapter is `LocalFsMediaStorage`, which stores re-encoded, thumbnailed images under a configured directory on the backend server (the same behaviour in dev and prod). No third-party object storage yet; an S3-compatible adapter is a post-v1 swap behind this same port, requiring no call-site changes.

## 5. Configuration & Profiles

| Profile | Purpose | Notes |
|---------|---------|-------|
| `dev` | Local development | Locally installed Postgres, seed data, verbose SQL logging, permissive CORS, 24 h access tokens for convenience, media written to a local directory. |
| `test` | Automated tests | Testcontainers Postgres, fixed clock, in-memory media storage, deterministic id generator. |
| `prod` | Deployment | Secrets from environment, JSON logs, strict CORS, HSTS, local filesystem media storage, Flyway validate-on-start with `clean` disabled. |

All secrets (`JWT_SECRET`, DB credentials) come from environment variables. `application.yml` holds no secret values, and the app fails fast at startup if a required secret is missing.

## 6. Error Handling

- `@RestControllerAdvice GlobalExceptionHandler` maps a sealed exception hierarchy to Problem Details (doc 05 §2).
- Domain exceptions: `DomainRuleViolation` (422), `NotFoundException` (404), `ConflictException` (409), `PermissionDeniedException` (403), `AuthenticationException` (401).
- Hibernate's `OptimisticLockException` → `409 VERSION_CONFLICT`.
- Unexpected exceptions log the full stack with the correlation id and return a bare `500` with that id and nothing else.

## 7. Performance Notes

| Risk | Mitigation |
|------|-----------|
| N+1 on tree reads | Flat native fetch + in-memory assembly; JPA is not used on the tree read path. |
| Per-node permission checks | Batch `EditableSet` (doc 04 §4.1). |
| Closure-table growth | Bounded by clan size; PK on `(ancestor_id, descendant_id)`; measured against the 5,000-person fixture. |
| Repeated grant lookups within a request | Request-scoped grant cache keyed by (userId, clanId). |
| Branch re-parent rewrite | Single recursive-CTE `UPDATE`, bounded by depth ≤ 10. |
| Large audit table | Monthly partitioning from Phase 8; reads always filtered by clan + time. |

## 8. Local Development

Run a local PostgreSQL 16 instance (a native install — no Docker in v1) and point `application-dev.yml` at it; the README documents the exact connection settings and how to create the database.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway runs on startup; the `dev` seed runner populates sample clans. Uploaded images are written to a local media directory (configurable), so no object-storage service is needed. Swagger UI is served at `http://localhost:8080/swagger-ui.html`.
