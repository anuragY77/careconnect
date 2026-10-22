# Project Memory: CareConnect

Current state of the project. Update after every task. Permanent decisions are in `DECISIONS.md`.

## Current Status
Phase 0 (foundation) is complete: CI is green on GitHub (backend, frontend, compose smoke test). Only the mentor review (TASK-001) is left on the owner's side. Phase 1 (identity, access, audit) is in progress.

## Completed (verified by the owner on his machine unless marked)
- Documents: PRD, ARCHITECTURE, PHASES, RULES, TASKS, DECISIONS, TEST_PLAN, SECURITY, DESIGN, PROMPTS, README
- TASK-002, 003: repository layout, documents in `docs/`
- TASK-004: Spring Boot 4.1.1 skeleton (Java 21 target, built on Java 25), deny-by-default security
- TASK-005: Angular 22.2 skeleton with Angular Material, self-hosted Inter font, training-use notice
- TASK-006, 007: Docker Compose (postgres 16, mailpit, backend, nginx frontend), env-driven config; all four services healthy
- TASK-008: Flyway baseline V1, real PostgreSQL integration test (Testcontainers)
- TASK-009: RFC 7807 error handling (`ErrorCode`, `ApiException`, `GlobalExceptionHandler`), 7 integration tests
- TASK-010: ArchUnit 1.5.1 architecture rules (13 rules)
- TASK-011, 012: GitHub Actions CI (backend, frontend with ESLint, compose smoke test); all three jobs green
- ADR-015: JVM always runs in UTC (Windows reports the legacy zone Asia/Calcutta, which postgres:16 rejects)
- TASK-013: Flyway V2 `app_user` (role and status check constraints, case-insensitive unique username and email, one portal account per patient, patient link rule), `AppUser` entity, `Role`, `UserStatus`, `AppUserRepository`
- TASK-014: BCrypt cost 12 (`PasswordEncoderConfig`), `PasswordPolicy` (12+ characters, at most 72 bytes, a letter, a digit, no username), `LocalAdminSeeder` (local profile only, password from `SEED_ADMIN_PASSWORD`, no default, weak password stops startup)
- TASK-015: `POST /api/auth/login` (`AuthController`, `LoginService`, lockout rules inside `AppUser`, row lock against parallel attempts, same 401 for every failure, constant-time-ish check for unknown users, `Clock` bean); returns the user, no token yet
- TASK-015a: per-IP failed-login limit (`SlidingWindowCounter`, `LoginThrottle`, HTTP 429 with `Retry-After`); nginx overwrites `X-Forwarded-For`; checked through the real compose stack (backend sees the network gateway address, not the nginx container) (ADR-016)
- Full backend run `mvn clean test` = 111 of 111 pass

## Current Task
TASK-016: JWT access token (15 minutes) and rotating opaque refresh token in an HttpOnly cookie (TC-03); reject the placeholder `JWT_SECRET` outside the local profile.

## Open Items
- Final deadline and demo date (not given yet)
- TASK-001: mentor review of `PRD.md` still to be done by the owner
- Theme: exact teal (#0F766E) from `DESIGN.md` is only a CSS variable; Angular Material still uses the cyan palette
- Phase 2 migration must add the foreign key from `app_user.linked_patient_id` to the patient table
- `must_change_password` is stored but not enforced yet; enforcement comes with login (TASK-015) and account management (TASK-019)
- TASK-016 must reject the placeholder `JWT_SECRET` from `.env.example` outside the local profile
- `axllent/mailpit` image is unpinned; pin it before any production-style deployment
- No backend lint tool besides ArchUnit
- Login rate limiting is in memory of one backend process; several backend instances need a shared store (ADR-016)
- Locking a known username for 15 minutes by failed logins is still possible; admin unlock comes with TASK-019

## Decided by the owner
- No MongoDB; audit trail in PostgreSQL (ADR-003)
- PostgreSQL stays; Oracle only if the mentor mandates it
- Lab orders (FR-24, FR-25) and the medication administration record (FR-30) are required (MUST)
- Deployment is local Docker Compose, built to production standards
- Production-level quality, no filler documents
- Changed files are shown in chat with a path heading and the complete code, not as zip files

## Known Issues and Environment
- The cloud workspace cannot reach Maven Central and has no Docker daemon, so backend code and Docker images are compiled and run only on the owner's laptop
- Owner's laptop: Windows, Java 25, a local PostgreSQL 18 on port 5432 (so his `.env` uses `POSTGRES_PORT=55432`)
- After changing `.env`, use `docker compose up -d --force-recreate backend`; plain `up` may keep the old container and its old environment
- A password used in a test must not contain the username it is checked against (the seeder test password was `Local-Admin-2026x` and was rejected; now `Seed-Local-2026x`)
