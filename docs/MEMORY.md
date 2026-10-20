# Project Memory: CareConnect

Current state of the project. Update after every task. Permanent decisions are in `DECISIONS.md`.

## Current Status
Planning and documentation. No code written yet.

## Completed
- Owner verified `docker compose up --build`: backend, postgres, mailpit healthy; frontend serves the page at http://localhost:8080. His machine already runs a local PostgreSQL 18 on 5432, so his .env uses POSTGRES_PORT=55432
- TASK-008: V1__baseline.sql (Flyway), DatabaseBaselineIntegrationTest (Testcontainers PostgreSQL 16: context starts with schema validation, Flyway V1 recorded, health public, other paths denied); pom gains spring-boot-testcontainers, testcontainers-junit-jupiter, testcontainers-postgresql (names checked against Testcontainers 2.0.5 docs). Compose: frontend bound to 127.0.0.1 and gets a healthcheck. NOT compiled in the cloud
- Owner verified on his laptop: `mvn clean test` BUILD SUCCESS (fresh compile), frontend `npm install`, 2/2 tests and production build pass, 0 vulnerabilities
- TASK-006, TASK-007: docker-compose.yml (postgres 16, mailpit, backend, frontend/nginx), Dockerfiles, nginx.conf with security headers and strict CSP, env-driven config, `.env.example` rewritten (backend DB settings derived from POSTGRES_* in compose). `docker compose config` validates in the cloud; images NOT built or run here (no Docker daemon). Angular production build now has no inline script/style (inlineCritical off) so CSP can stay strict
- TASK-004 verified by owner: `mvn test` BUILD SUCCESS, 1 test passed, on Java 25.0.2 (project targets Java 21)
- TASK-005: Angular 22.2 skeleton in frontend/ with Angular Material (M3 cyan/orange theme for now; exact teal brand theme from DESIGN.md still to do), self-hosted Inter font, app shell with training-use notice, `ng build` and `ng test` pass in the cloud (Node 24 used there)
- TASK-004: backend skeleton (Spring Boot 4.1.1, Java 21, Maven; starters webmvc, validation, security, actuator, data-jpa, flyway; deny-by-default SecurityConfig; toolchain test). NOT compiled in the cloud (Maven Central blocked); owner verifies locally
- TASK-002, TASK-003: repo at /home/claude/careconnect (backend, frontend, docs, ops), docs moved into docs/, git initialised on main
- `PRD.md` first draft (SRS and OOAD format, 40 requirements, 34 use cases, diagrams, data model, traceability)
- `ARCHITECTURE.md`, `PHASES.md`, `RULES.md`, `TASKS.md`, `DECISIONS.md`, `TEST_PLAN.md`, `SECURITY.md`, `DESIGN.md`, `PROMPTS.md`, `README.md`, `.env.example` drafted

## Current Task
TASK-008 written, waiting for owner's `mvn clean test` (needs Docker running; first run pulls testcontainers images) and `docker compose up --build -d` rebuild result. Next: TASK-009 RFC 7807 error handling.

## Open Items
- Final deadline and demo date (not given yet)

## Decided by the owner
- No MongoDB; audit trail in PostgreSQL (ADR-003)
- PostgreSQL stays; Oracle only if mentor mandates
- Lab orders (FR-24, FR-25) and medication administration record (FR-30) are required (MUST)
- Deployment is local Docker Compose, built to production standards
- Production-level quality; no filler documents

## Known Issues
- Cloud container has no Docker daemon: docker compose and Testcontainers cannot run here. Local PostgreSQL 16 server binaries are available for integration tests; compose files are written here and verified on the owner's machine.
- Toolchain here: Java 21, Maven 3.9.11, Node 22, npm 10, PostgreSQL 16.

## Next Step
Review and correct the documents, then start Phase 0 (TASK-002 onward).
