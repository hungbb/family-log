# 03 — Data Model &amp; Database Design

> PostgreSQL 16 + Flyway. Physical realization of [02-domain-model.md](02-domain-model.md).

---

## 1. Conventions


| Rule            | Choice                                                                                          |
| --------------- | ----------------------------------------------------------------------------------------------- |
| Naming          | `snake_case`, plural tables (`persons`, `clans`), singular column names.                        |
| Primary keys    | `UUID` (v7 where available, generated in the application for insert-order locality).            |
| Timestamps      | `TIMESTAMPTZ`, UTC, columns `created_at`, `updated_at`, `deleted_at`.                           |
| Audit columns   | `created_by`, `updated_by` (`UUID` → `users.id`, nullable for system actions).                  |
| Soft delete     | `deleted_at IS NULL` predicate on every read; partial indexes carry the same predicate.         |
| Optimistic lock | `version BIGINT NOT NULL DEFAULT 0` on `clans`, `branches`, `persons`.                          |
| Enums           | Stored as `VARCHAR` + `CHECK` constraint, **not** native PG enums (cheap to extend via Flyway). |
| Foreign keys    | Always declared; `ON DELETE RESTRICT` by default, `CASCADE` only for owned child rows.          |
| Migrations      | `V<version>__<snake_case_description>.sql`, forward-only, never edited after merge.             |


## 2. Entity–Relationship Overview

```
users ──< refresh_tokens
  │
  ├──< clan_memberships >── clans ──< branches (self-ref tree)
  │                            │          │
  │                            │          └── branch_root_person_id ──┐
  ├──< clan_join_requests >────┤                                      │
  │                            │                                      ▼
  ├──< permission_grants >─────┤                              persons (clan tree)
  │                            │                              │  │  │
  ├──< node_claim_requests >───┼──────────────────────────────┘  │  │
  │                            │                                 │  │
  └── persons.linked_user_id ──┴─────────────────────────────────┘  │
                                                                    │
                     parentages (parent → child)  ──────────────────┤
                     unions (partner_a, partner_b) ─────────────────┤
                     person_ancestry (closure projection) ──────────┘

audit_entries, notifications, media_objects  (supporting, clan-scoped where applicable)
```

## 3. Table Definitions

### 3.1 `users`


| Column                     | Type                      | Notes                          |
| -------------------------- | ------------------------- | ------------------------------ |
| `id`                       | UUID PK                   |                                |
| `email`                    | VARCHAR(320)              | `UNIQUE`, normalized lowercase |
| `password_hash`            | VARCHAR(100)              | BCrypt                         |
| `display_name`             | VARCHAR(150)              |                                |
| `avatar_media_id`          | UUID FK → `media_objects` | nullable                       |
| `locale`                   | VARCHAR(10)               | default `vi-VN`                |
| `status`                   | VARCHAR(30)               | `ACTIVE                        |
| `email_verified_at`        | TIMESTAMPTZ               | nullable                       |
| `last_login_at`            | TIMESTAMPTZ               | nullable                       |
| `created_at`, `updated_at` | TIMESTAMPTZ               |                                |


Indexes: `ux_users_email (lower(email))`.

### 3.2 `refresh_tokens`


| Column                     | Type            | Notes                                                      |
| -------------------------- | --------------- | ---------------------------------------------------------- |
| `id`                       | UUID PK         |                                                            |
| `user_id`                  | UUID FK → users | `ON DELETE CASCADE`                                        |
| `token_hash`               | VARCHAR(100)    | SHA-256 of the opaque token; the raw value is never stored |
| `issued_at`, `expires_at`  | TIMESTAMPTZ     |                                                            |
| `revoked_at`               | TIMESTAMPTZ     | nullable                                                   |
| `user_agent`, `ip_address` | VARCHAR         | session hygiene                                            |


Indexes: `ux_refresh_token_hash (token_hash)`, `ix_refresh_user_active (user_id) WHERE revoked_at IS NULL`.

Also: `verification_tokens` (same shape, plus `purpose` = `EMAIL_VERIFY|PASSWORD_RESET`).

### 3.3 `clans`


| Column                                                               | Type                    | Notes                                     |
| -------------------------------------------------------------------- | ----------------------- | ----------------------------------------- |
| `id`                                                                 | UUID PK                 |                                           |
| `clan_code`                                                          | VARCHAR(32)             | `UNIQUE`, immutable                       |
| `name`                                                               | VARCHAR(200)            |                                           |
| `description`                                                        | TEXT                    |                                           |
| `origin_place`                                                       | VARCHAR(255)            | quê quán                                  |
| `cover_media_id`                                                     | UUID FK → media_objects | nullable                                  |
| `visibility`                                                         | VARCHAR(20)             | `PRIVATE` in v1                           |
| `root_branch_id`                                                     | UUID FK → branches      | deferrable, set right after branch insert |
| `version`                                                            | BIGINT                  |                                           |
| `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted_at` |                         |                                           |


### 3.4 `branches`


| Column                   | Type               | Notes                                    |
| ------------------------ | ------------------ | ---------------------------------------- |
| `id`                     | UUID PK            |                                          |
| `clan_id`                | UUID FK → clans    | `ON DELETE CASCADE`                      |
| `parent_branch_id`       | UUID FK → branches | nullable = root branch                   |
| `branch_code`            | VARCHAR(32)        | `UNIQUE`                                 |
| `name`                   | VARCHAR(200)       |                                          |
| `description`            | TEXT               |                                          |
| `branch_root_person_id`  | UUID FK → persons  | nullable                                 |
| `path`                   | VARCHAR(512)       | materialized, e.g. `/a1b2c3d4/9f8e7d6c/` |
| `depth`                  | SMALLINT           | 0 for root, `CHECK depth <= 10`          |
| `archived_at`            | TIMESTAMPTZ        | nullable                                 |
| `version`, audit columns |                    |                                          |


Indexes:

- `ux_branches_code (branch_code)`
- `ix_branches_clan (clan_id) WHERE deleted_at IS NULL`
- `ix_branches_path (clan_id, path varchar_pattern_ops)` — powers `path LIKE '/a1b2c3d4/%'` descendant lookups
- `ux_branches_one_root (clan_id) WHERE parent_branch_id IS NULL AND deleted_at IS NULL` — enforces I-C2

### 3.5 `clan_memberships`


| Column                 | Type               | Notes    |
| ---------------------- | ------------------ | -------- |
| `id`                   | UUID PK            |          |
| `clan_id`, `user_id`   | UUID FK            |          |
| `home_branch_id`       | UUID FK → branches | nullable |
| `status`               | VARCHAR(20)        | `ACTIVE  |
| `joined_at`, `left_at` | TIMESTAMPTZ        |          |
| `approved_by`          | UUID FK → users    | nullable |


Indexes: `ux_membership_active (clan_id, user_id) WHERE status = 'ACTIVE'` (I-M1), `ix_membership_user (user_id) WHERE status = 'ACTIVE'` (clan switcher).

### 3.6 `clan_join_requests`


| Column                                        | Type               | Notes                 |
| --------------------------------------------- | ------------------ | --------------------- |
| `id`                                          | UUID PK            |                       |
| `clan_id`                                     | UUID FK            |                       |
| `target_branch_id`                            | UUID FK → branches | nullable = whole clan |
| `requester_user_id`                           | UUID FK → users    |                       |
| `message`                                     | TEXT               |                       |
| `status`                                      | VARCHAR(20)        | `PENDING              |
| `decided_by`, `decided_at`, `decision_reason` |                    |                       |
| `expires_at`                                  | TIMESTAMPTZ        | created_at + 90 days  |


Indexes: `ux_join_pending (requester_user_id, clan_id, COALESCE(target_branch_id, '000…')) WHERE status='PENDING'` (I-J1), `ix_join_clan_status (clan_id, status, created_at DESC)` (approver inbox).

### 3.7 `persons`


| Column                                                                                         | Type                    | Notes                                |
| ---------------------------------------------------------------------------------------------- | ----------------------- | ------------------------------------ |
| `id`                                                                                           | UUID PK                 |                                      |
| `clan_id`                                                                                      | UUID FK → clans         | **tenancy key, on every query**      |
| `branch_id`                                                                                    | UUID FK → branches      | organizational placement             |
| `full_name`                                                                                    | VARCHAR(200)            | `CHECK length(btrim(full_name)) > 0` |
| `family_name`, `given_name`, `alias`, `posthumous_name`                                        | VARCHAR(100)            | nullable                             |
| `name_search`                                                                                  | TSVECTOR                | generated, unaccented; see §6        |
| `gender`                                                                                       | VARCHAR(10)             | `MALE                                |
| `life_status`                                                                                  | VARCHAR(10)             | `ALIVE                               |
| `birth_year`, `birth_month`, `birth_day`                                                       | SMALLINT                | nullable, partial precision          |
| `birth_precision`                                                                              | VARCHAR(10)             | `EXACT                               |
| `birth_circa`                                                                                  | BOOLEAN                 | default false                        |
| `birth_lunar_text`                                                                             | VARCHAR(100)            | optional âm lịch record              |
| `death_year`, `death_month`, `death_day`, `death_precision`, `death_circa`, `death_lunar_text` |                         | same shape as birth                  |
| `burial_place`                                                                                 | VARCHAR(255)            |                                      |
| `generation`                                                                                   | SMALLINT                | đời; nullable while unknown          |
| `birth_order`                                                                                  | SMALLINT                | sibling ordering                     |
| `avatar_media_id`                                                                              | UUID FK → media_objects | nullable                             |
| `notes_html`                                                                                   | TEXT                    | sanitized                            |
| `notes_text`                                                                                   | TEXT                    | plain projection for search          |
| `linked_user_id`                                                                               | UUID FK → users         | nullable                             |
| `is_founding_ancestor`                                                                         | BOOLEAN                 | default false                        |
| `version`, `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted_at`                |                         |                                      |


Indexes / constraints:

- `ix_persons_clan (clan_id) WHERE deleted_at IS NULL`
- `ix_persons_branch (clan_id, branch_id) WHERE deleted_at IS NULL`
- `ux_persons_linked_user (clan_id, linked_user_id) WHERE linked_user_id IS NOT NULL AND deleted_at IS NULL` — I-P4
- `ux_persons_founding (clan_id) WHERE is_founding_ancestor AND deleted_at IS NULL` — I-P6
- `ix_persons_name_search GIN (name_search)`
- `ix_persons_generation (clan_id, generation) WHERE deleted_at IS NULL`
- `CHECK (life_status <> 'ALIVE' OR death_year IS NULL)` — I-P3

### 3.8 `parentages`


| Column                                | Type              | Notes                              |
| ------------------------------------- | ----------------- | ---------------------------------- |
| `id`                                  | UUID PK           |                                    |
| `clan_id`                             | UUID FK           | denormalized for tenancy filtering |
| `parent_person_id`, `child_person_id` | UUID FK → persons |                                    |
| `parent_role`                         | VARCHAR(10)       | `FATHER                            |
| `nature`                              | VARCHAR(12)       | `BIOLOGICAL                        |
| `union_id`                            | UUID FK → unions  | nullable                           |
| audit columns, `deleted_at`           |                   |                                    |


Constraints / indexes:

- `CHECK (parent_person_id <> child_person_id)` — I-R1
- `ux_parentage_child_role (child_person_id, parent_role) WHERE deleted_at IS NULL` — I-R3
- `ix_parentage_parent (parent_person_id) WHERE deleted_at IS NULL` — children lookup
- `ix_parentage_clan (clan_id) WHERE deleted_at IS NULL`
- Cycle prevention (I-R4) is enforced in the application against `person_ancestry` before insert.

### 3.9 `unions`


| Column                         | Type                 | Notes                                                       |
| ------------------------------ | -------------------- | ----------------------------------------------------------- |
| `id`                           | UUID PK              |                                                             |
| `clan_id`                      | UUID FK              |                                                             |
| `partner_a_id`, `partner_b_id` | UUID FK → persons    | stored with `partner_a_id < partner_b_id` (canonical order) |
| `union_type`                   | VARCHAR(12)          | `PRIMARY                                                    |
| `status`                       | VARCHAR(12)          | `ACTIVE                                                     |
| `start_*`, `end_*`             | partial-date columns | same shape as person dates                                  |
| `notes`                        | TEXT                 |                                                             |
| audit columns, `deleted_at`    |                      |                                                             |


Constraints: `CHECK (partner_a_id <> partner_b_id)`, `ux_union_pair (partner_a_id, partner_b_id) WHERE deleted_at IS NULL` (I-N2). Canonical ordering makes the unordered-pair uniqueness a plain unique index.

### 3.10 `person_ancestry` (closure projection)

Derived read model that makes "is X a descendant of Y" a single indexed lookup — required by `SUBTREE`-scoped authorization (FR-Z4) and by fast subtree reads.


| Column          | Type              | Notes        |
| --------------- | ----------------- | ------------ |
| `clan_id`       | UUID              |              |
| `ancestor_id`   | UUID FK → persons |              |
| `descendant_id` | UUID FK → persons |              |
| `depth`         | SMALLINT          | 0 = self-row |


PK `(ancestor_id, descendant_id)`; index `ix_ancestry_descendant (descendant_id)`; index `ix_ancestry_clan_ancestor (clan_id, ancestor_id)`.

**Semantics.** Parentage is a DAG (two parents per child), so a person has many ancestors; the closure stores the transitive ancestor set, not a single path. Every person has a self-row at depth 0.

**Maintenance.** Rebuilt inside the same transaction as any parentage change:

- *Add edge (P → C)*: insert the cross product `ancestors(P) ∪ {P}` × `descendants(C) ∪ {C}`, skipping existing pairs.
- *Remove edge*: recompute the closure for `descendants(C) ∪ {C}` via a recursive CTE (bounded and cheap at family-tree scale).
- A `rebuild_person_ancestry(clan_id)` maintenance function exists for repair and is verified by a scheduled consistency check (Phase 8).

**Alternative considered.** Pure recursive CTEs at query time — simpler, no projection to maintain, but puts a recursive walk on the hot authorization path for every mutation. Rejected for NFR-1; the closure table is introduced in Phase 4 alongside relationships.

### 3.11 `permission_grants`


| Column                                   | Type            | Notes                                |
| ---------------------------------------- | --------------- | ------------------------------------ |
| `id`                                     | UUID PK         |                                      |
| `clan_id`                                | UUID FK         |                                      |
| `grantee_user_id`                        | UUID FK → users |                                      |
| `permission`                             | VARCHAR(30)     | six values, `CHECK`-constrained      |
| `scope_type`                             | VARCHAR(10)     | `CLAN                                |
| `scope_ref_id`                           | UUID            | branch or person id; NULL for `CLAN` |
| `granted_by`                             | UUID FK → users |                                      |
| `granted_at`, `expires_at`, `revoked_at` | TIMESTAMPTZ     |                                      |
| `revoked_by`                             | UUID FK → users | nullable                             |


Constraints / indexes:

- `CHECK ((scope_type = 'CLAN') = (scope_ref_id IS NULL))` — I-G1
- `CHECK (permission <> 'CLAN_ADMIN' OR scope_type = 'CLAN')` — I-G2
- `ux_grant_active (grantee_user_id, clan_id, permission, scope_type, COALESCE(scope_ref_id, '000…')) WHERE revoked_at IS NULL` — I-G5
- `ix_grant_lookup (grantee_user_id, clan_id) WHERE revoked_at IS NULL` — the hot path: load all of a user's grants for one clan in one query

### 3.12 `node_claim_requests`


| Column                                        | Type        | Notes    |
| --------------------------------------------- | ----------- | -------- |
| `id`                                          | UUID PK     |          |
| `clan_id`, `person_id`, `requester_user_id`   | UUID FK     |          |
| `evidence`                                    | TEXT        |          |
| `status`                                      | VARCHAR(20) | `PENDING |
| `decided_by`, `decided_at`, `decision_reason` |             |          |


Indexes: `ux_claim_pending (requester_user_id, person_id) WHERE status='PENDING'` (I-K3), `ix_claim_clan_status (clan_id, status, created_at DESC)` (approver inbox).

### 3.13 Supporting tables

`**audit_entries**` — `id`, `clan_id?`, `actor_user_id?`, `action` (e.g. `PERSON_UPDATED`), `target_type`, `target_id`, `before_json JSONB`, `after_json JSONB`, `authorizing_grant_id?`, `ip_address`, `correlation_id`, `occurred_at`. Append-only: no `UPDATE`/`DELETE` grant for the application role. Index `ix_audit_clan_time (clan_id, occurred_at DESC)`, `ix_audit_target (target_type, target_id)`. Partitioned by month from Phase 8.

`**notifications**` — `id`, `recipient_user_id`, `clan_id?`, `type`, `payload JSONB`, `read_at`, `created_at`. Index `ix_notif_unread (recipient_user_id, created_at DESC) WHERE read_at IS NULL`.

`**media_objects**` — `id`, `storage_key`, `content_type`, `byte_size`, `width`, `height`, `variants JSONB`, `owner_user_id`, `clan_id?`, `created_at`. The single indirection point between DB rows and the image files stored on the server's local filesystem.

## 4. Reference Queries

**Descendants of a person (subtree read), depth-bounded:**

```sql
SELECT p.*
FROM person_ancestry a
JOIN persons p ON p.id = a.descendant_id
WHERE a.ancestor_id = :rootPersonId
  AND a.clan_id = :clanId
  AND a.depth <= :maxDepth
  AND p.deleted_at IS NULL;
```

**Is a person inside a branch scope (including descendant branches):**

```sql
SELECT EXISTS (
  SELECT 1
  FROM persons p
  JOIN branches b  ON b.id = p.branch_id
  JOIN branches sb ON sb.id = :scopeBranchId
  WHERE p.id = :personId
    AND p.clan_id = :clanId
    AND b.path LIKE sb.path || '%'
);
```

**Load a user's effective grants for one clan (single round trip, cached per request):**

```sql
SELECT permission, scope_type, scope_ref_id
FROM permission_grants
WHERE grantee_user_id = :userId
  AND clan_id = :clanId
  AND revoked_at IS NULL
  AND (expires_at IS NULL OR expires_at > now());
```

**Whole-clan tree read (master view, FR-T2):** a single flat fetch of `persons` + `parentages` + `unions` for the clan, assembled into a tree in the application. For a 2,000-node clan this is three indexed scans and beats per-node recursion by a wide margin.

## 5. Flyway Migration Plan

Forward-only, one concern per migration, aligned with the phases in [08-implementation-roadmap.md](08-implementation-roadmap.md).


| Version                                        | Migration                                                             | Phase                                   |
| ---------------------------------------------- | --------------------------------------------------------------------- | --------------------------------------- |
| `V1__enable_extensions.sql`                    | `pgcrypto`, `unaccent`, `pg_trgm`                                     | 0                                       |
| `V2__create_users_and_tokens.sql`              | `users`, `refresh_tokens`, `verification_tokens`                      | 1                                       |
| `V3__create_media_objects.sql`                 | `media_objects` (referenced early by users/clans)                     | 1                                       |
| `V4__create_clans_and_branches.sql`            | `clans`, `branches`, path/root constraints                            | 2                                       |
| `V5__create_memberships_and_join_requests.sql` | `clan_memberships`, `clan_join_requests`                              | 2                                       |
| `V6__create_permission_grants.sql`             | `permission_grants` + constraints                                     | 3                                       |
| `V7__create_persons.sql`                       | `persons`, name search vector, indexes                                | 4                                       |
| `V8__create_parentages_and_unions.sql`         | `parentages`, `unions`                                                | 4                                       |
| `V9__create_person_ancestry.sql`               | closure table + `rebuild_person_ancestry()`                           | 4                                       |
| `V10__link_branches_to_root_person.sql`        | `branches.branch_root_person_id` FK (deferred until `persons` exists) | 4                                       |
| `V11__create_node_claim_requests.sql`          | claim workflow                                                        | 6                                       |
| `V12__create_audit_entries.sql`                | audit log                                                             | 3 (created early, populated throughout) |
| `V13__create_notifications.sql`                | notifications                                                         | 6                                       |
| `V14__add_search_indexes.sql`                  | trigram/GIN tuning, generation index                                  | 8                                       |
| `V15__partition_audit_entries.sql`             | monthly partitioning                                                  | 8                                       |


**Rules.** Repeatable migrations (`R__`) only for views and functions. Every migration is tested against a Testcontainers Postgres. Destructive changes ship in two steps (add-and-backfill, then drop in a later release).

## 6. Vietnamese Text Search

Vietnamese diacritics make naïve `LIKE` search unusable ("Nguyễn" vs "Nguyen"). Approach:

1. `unaccent` extension plus an immutable wrapper `f_unaccent(text)`.
2. Generated column: `name_search tsvector GENERATED ALWAYS AS (to_tsvector('simple', f_unaccent(coalesce(full_name,'') || ' ' || coalesce(alias,'') || ' ' || coalesce(posthumous_name,'')))) STORED`, indexed with GIN.
3. Prefix/typo search uses a `pg_trgm` index on `f_unaccent(lower(full_name))`.
4. Queries unaccent the input the same way, so "nguyen van a" matches "Nguyễn Văn An".

Postgres full-text search covers v1's scale (NFR-2); an external search engine is out of scope.

## 7. Seed &amp; Test Data

- `dev` profile seeds: 2 clans, one 4-generation tree (~40 persons) with 3 branches, 6 users covering every persona, and one pending join request plus one pending claim.
- A `LargeTreeGenerator` test fixture produces a 5,000-person clan for performance tests (NFR-1).
- Seeds live in `src/main/resources/db/seed/` and are applied by a `dev`-profile runner, never by Flyway, so production migrations stay data-free.

