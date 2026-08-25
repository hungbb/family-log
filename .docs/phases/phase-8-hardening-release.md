# Phase 8 — Hardening & Release

**Goal:** turn a feature-complete application into something a family can trust with a hundred years of records (M6).

**Estimate:** ~2 weeks · **Depends on:** [Phase 7](phase-7-media-content.md) · **Next:** post-v1 backlog

**Requirements:** FR-X3, NFR-2, NFR-3 (full), NFR-5, NFR-9, NFR-10

---

## 1. Scope

**In scope** — full security review, performance and load validation at the NFR-2 scale target, the audit-log viewer, email delivery, data export/print, backup and restore rehearsal, observability, production deployment, and the operational runbook.

**Out of scope** — GEDCOM import (post-v1), native mobile (N1), public sharing links (Q1 default: no), horizontal scaling beyond a single primary.

## 2. Security Hardening

| # | Task | Detail |
|---|------|--------|
| S8.1 | OWASP Top-10 review with a written checklist per item | Findings tracked as issues, all high/critical fixed before release |
| S8.2 | Authorization re-audit | Re-run the isolation matrix; grep every mutating endpoint for its `require(...)`; confirm the ArchUnit rule still covers every service |
| S8.3 | Dependency scanning (and container scanning once images exist) | Delivered with the CI/CD workstream that lands in this phase; build fails on high/critical CVEs |
| S8.4 | SAST (CodeQL or equivalent) on both applications | |
| S8.5 | Security headers | HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, strict CSP on the web app (no `unsafe-inline` — styled-components uses a nonce or hash) |
| S8.6 | Rate limiting extended beyond auth | Per-user global budget, plus stricter limits on search and tree endpoints |
| S8.7 | Secret handling review | No secrets in the image or repo; startup fails loudly when one is missing; rotation procedure documented |
| S8.8 | Privacy pass (NFR-9) | Living persons' sensitive fields never leave the API for non-members; a test enumerates every DTO field against a sensitivity classification |
| S8.9 | Penetration-style tests for the tenancy boundary | Cross-clan id substitution attempted on every nested endpoint |
| S8.10 | Account-security polish | Session list with device/IP, suspicious-login notification, password-change confirmation |

## 3. Performance & Scale

| # | Task | Detail |
|---|------|--------|
| P8.1 | Load test at the NFR-2 target | 10k clans / 200k persons / 50k users seeded; k6 or Gatling scenarios for read-heavy and mixed workloads |
| P8.2 | Query plan review | `EXPLAIN ANALYZE` on the tree read, search, grant lookup, and closure maintenance; `V14__add_search_indexes.sql` applies the findings |
| P8.3 | Connection pool and JVM tuning | HikariCP sizing, heap and GC settings recorded with the reasoning |
| P8.4 | Frontend budget verification | Bundle ≤ 250 KB gzipped, Lighthouse ≥ 90/95, tree render budgets re-measured |
| P8.5 | Cache review | Tree cache hit rate, per-request grant cache, ETag effectiveness measured under load |
| P8.6 | Closure-table consistency job | Nightly verification comparing `person_ancestry` against a recomputed reference for a sample of clans; alerts on drift (R3) |

## 4. Data, Backup & Operations

| # | Task | Detail |
|---|------|--------|
| O8.1 | Automated daily backups with 30-day retention, plus PITR via WAL archiving | |
| O8.2 | **Restore rehearsal** (NFR-5) | Restore into a clean environment, verify data integrity, record the actual RTO. A backup that has never been restored is not a backup |
| O8.3 | Soft-delete retention job | Hard-delete after 30 days (NFR-9), with a documented recovery path before that |
| O8.4 | Audit-log partitioning | `V15__partition_audit_entries.sql`, monthly partitions with a retention policy |
| O8.5 | Observability | Prometheus metrics, Grafana dashboards (latency, error rate, DB pool, tree cache), alerts on error rate, p95 latency, and DB saturation |
| O8.6 | Structured-log review | Correlation ids present end to end; no PII in logs |
| O8.7 | CI/CD + production deployment pipeline (the deferred workstream lands here) | Build artifact/image, containerization, migrations run before the app starts, health-gated rollout, documented rollback |
| O8.8 | **Runbook** | Deploy, rollback, restore, rotate secrets, investigate an authorization complaint, repair the closure table, handle a suspended account |

## 5. Remaining Features

| # | Task | Requirement |
|---|------|-------------|
| R8.1 | Email delivery (SMTP/provider) for verification, password reset, and opt-in notifications | FR-X3, FR-A6 |
| R8.2 | Audit-log viewer for `CLAN_ADMIN`: filters by action, actor, target, date; shows the authorizing grant | FR-X1 |
| R8.3 | Clan data export (JSON + printable HTML/PDF of the tree and person records) | NFR-9 practical data ownership; also the most-requested "give me my family book back" feature |
| R8.4 | Print stylesheet for the list view and a generation-by-generation printable layout | Families still want paper |
| R8.5 | Browser compatibility pass (NFR-10) | Latest two versions of Chrome, Edge, Firefox, Safari; responsive down to 360 px |
| R8.6 | Empty states, error pages, offline detection, and copy review across the app | |
| R8.7 | Onboarding polish: a guided first-clan walkthrough | Reduces the "empty tree" drop-off |

## 6. Release Readiness Checklist

- [ ] All six PRD acceptance scenarios pass on staging with production configuration.
- [ ] Security review complete; no open high or critical findings.
- [ ] Load test at NFR-2 scale meets the NFR-1 latency budgets.
- [ ] Backup taken, restored into a clean environment, RTO measured and recorded.
- [ ] Monitoring and alerting live, with someone actually receiving the alerts.
- [ ] Runbook reviewed by someone who did not write it.
- [ ] `.docs` updated to match what was built; every deviation has an ADR.
- [ ] Rollback tested by rolling back a real deployment.
- [ ] Legal/privacy notice and terms published, with a data-deletion path.
- [ ] A real family has used it end to end and their feedback is triaged.

## 7. Post-v1 Backlog (recorded, not scheduled)

| Item | Notes |
|------|-------|
| GEDCOM import/export | The obvious migration path from existing genealogy software (Q4) |
| Photo galleries and document scans per person | Schema already accommodates it |
| Public read-only sharing links | Depends on Q1 |
| Real-time collaboration | Requires a different concurrency model than optimistic locking (N4) |
| Cross-clan person linking | Marriages between two clans on the platform (Q5) |
| Lunar-calendar conversion and giỗ (death anniversary) reminders | High cultural value; the data is already captured (Q2) |
| Native mobile apps | N1 |
| Named role bundles over grants | Convenience layer atop the permission model, if families ask for "make him a branch head" |
| Merge duplicate person nodes | Inevitable once several editors work in parallel |

## 8. Notes & Risks

- **Do not let hardening compress.** The temptation at this stage is to ship after the feature work and treat this phase as optional. The restore rehearsal and the authorization re-audit are the two items that most repay the time.
- **The export feature is trust infrastructure.** Families are handing over a hundred years of records; being visibly able to take the data back out is what makes the platform safe to adopt.
- **Watch the first real clan closely.** Their first week produces more design signal than the whole test suite — particularly around duplicate nodes, unusual family structures, and who expects to be able to edit what.
