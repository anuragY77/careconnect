# Architecture Decisions: CareConnect

Permanent decisions and the reasons for them. Do not reverse a decision without adding a new entry that supersedes it. Current project state lives in `MEMORY.md`.

Status values: **Accepted**, **Proposed** (needs owner or mentor confirmation), **Superseded**.

## ADR-001: Java, Spring Boot and Angular
- **Status:** Accepted
- **Decision:** Backend in Java with Spring Boot; frontend in Angular.
- **Reason:** Required by the project brief.

## ADR-002: PostgreSQL as the primary database
- **Status:** Accepted
- **Decision:** Use PostgreSQL for all transactional clinical data. Oracle is not used.
- **Reason:** Owner decision after review: Oracle is not compulsory; use it only if the mentor mandates it. PostgreSQL is free, familiar to the developer and fully capable. Persistence uses JPA and Flyway, and Postgres-only features are limited to JSONB in two columns, so switching later is possible with limited work.

## ADR-003: No MongoDB; audit trail in PostgreSQL
- **Status:** Accepted (owner decision)
- **Decision:** MongoDB is not used. The append-only `audit_event` table lives in PostgreSQL, with INSERT and SELECT only for the application role and a trigger blocking UPDATE and DELETE. Access is behind an `AuditStore` interface.
- **Reason:** Owner decision. One database is simpler to run, back up and test, and writing the audit row in the same transaction as the action gives stronger consistency than a second database.
- **Consequence:** Audit volume shares the primary database; indexes and, if ever needed, partitioning by month handle growth. The `AuditStore` interface allows moving it later.

## ADR-004: Modular monolith
- **Status:** Accepted
- **Decision:** One Spring Boot application with enforced module boundaries (ArchUnit).
- **Reason:** One developer, strong transactional needs, simple deployment. Boundaries keep a later split possible.

## ADR-005: Single Angular app with two shells
- **Status:** Accepted
- **Decision:** One Angular application with lazy-loaded `/staff` and `/portal` route trees.
- **Reason:** Shared components, auth and API client; less to build and maintain. Security is enforced on the server, not by separation of apps.

## ADR-006: JWT access token with refresh cookie
- **Status:** Accepted
- **Decision:** 15-minute JWT access token kept in memory; refresh token stored hashed in the database, rotated on use, sent as HttpOnly, Secure, SameSite=Strict cookie.
- **Reason:** Limits exposure from script injection and token theft compared with long-lived tokens in local storage.

## ADR-007: No hard deletes of clinical data
- **Status:** Accepted
- **Decision:** Clinical records are never deleted; status values (inactive, entered-in-error, discontinued) with a reason are used.
- **Reason:** Medical record integrity and audit requirements (BR-08, BR-16).

## ADR-008: Demo clinical data, not clinical grade
- **Status:** Accepted
- **Decision:** The formulary, interaction rules, dose ranges and ICD-10 subset are demo data reviewed by the project owner. The system displays a notice that it is not for clinical use.
- **Reason:** No licensed drug database is available and the project is for training.

## ADR-009: Safety checks as a strategy pattern, re-run at signing
- **Status:** Accepted
- **Decision:** Each check is a `SafetyRule`; all are re-run on the server at signing time; contraindicated and duplicate cases are not overridable.
- **Reason:** Extensible design and protection against stale client-side results.

## ADR-010: Audit is fail-closed
- **Status:** Accepted
- **Decision:** If an audit event cannot be written, the operation fails.
- **Reason:** Unaudited access to patient data is unacceptable even if it costs availability in a demo.

## ADR-011: Documents before code
- **Status:** Accepted
- **Decision:** `PRD.md`, `ARCHITECTURE.md`, `PHASES.md`, `RULES.md`, `TASKS.md` and the supporting documents exist and are reviewed before feature coding starts.
- **Reason:** Gives the AI assistant stable context, prevents scope drift and matches the mentor's documentation expectation.

## ADR-012: Signals for frontend state, no store library
- **Status:** Accepted
- **Decision:** Use Angular signals in feature services; add a store library only if a documented need appears.
- **Reason:** Less boilerplate for the size of this application.

## ADR-013: Requirement priorities (MoSCoW) and MVP cut line
- **Status:** Accepted
- **Decision:** FR priorities M, S, C as in `PRD.md`; MVP is all M items. Safety, immutability, authorization and audit are never cut.
- **Reason:** Protects the most valuable and risk-sensitive parts when time is short.

## ADR-014: Versions pinned at setup
- **Status:** Proposed
- **Decision:** At Phase 0, check official documentation for current LTS and stable versions of Java, Spring Boot, Angular, and PostgreSQL, and record them here.
- **Reason:** Avoids relying on remembered versions.
- **Recorded versions (checked 2026-10-19 on endoflife.date and the Spring Boot 4.0 migration guide):** Spring Boot 4.1.1 (3.5.x is already out of open-source support, 4.0.x ends 2026-12-31); Java 21 LTS (Boot 4 needs 17 or later); PostgreSQL 16 for the compose database (18 is current; 16 is supported until 2028-11 and matches the developer machine); Angular: to be recorded in TASK-005 (22.x is current).
