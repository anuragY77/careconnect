# CareConnect: Patient–Provider EHR

A web-based Electronic Health Record system covering patient records, clinical documentation, computerized provider order entry (CPOE), medication management and a patient portal.

> **Training and demonstration project.** It uses synthetic data only and demo clinical rules. It must not be used for real patient care.

## Status

Work is tracked by task in [`docs/TASKS.md`](docs/TASKS.md) and summarised in [`docs/MEMORY.md`](docs/MEMORY.md). There is no fixed date; the table shows where the project stands.

| Area | State |
|---|---|
| Foundation: repository, Docker Compose, CI, Flyway, error handling, architecture tests | Done |
| Sign-in: login, lockout, per-IP limit, access token, rotating refresh token | Done |
| Access control: security chain, role checks on every endpoint, authorization matrix test | Done |
| Staff accounts: create, update, deactivate, unlock, reset password, forced password change | Done |
| Audit trail: append-only `audit_event` table and `AuditStore` | Done (tests green) |
| Audit capture: `@Audited`, login audit, fail-closed test | Next |
| Frontend: design foundation and login layout done; login wiring, guards, admin screen | In progress |
| Patients, scheduling, clinical notes, orders, medication, lab, portal | Not started |

Backend test suite: `mvn clean test` runs 226 tests (JUnit 5, Testcontainers with PostgreSQL 16, ArchUnit with 14 architecture rules).

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1 (modular monolith) |
| Frontend | Angular 22.2, Tailwind 4 |
| Database | PostgreSQL 16 (clinical data and the audit trail) |
| Migrations | Flyway |
| Runtime | Docker Compose (PostgreSQL, Mailpit, backend, nginx frontend) |
| Testing | JUnit 5, Testcontainers, ArchUnit |
| CI | GitHub Actions (backend, frontend, compose smoke test) |

## What works today

**Authentication.** `POST /api/auth/login` returns a 15-minute access token (JWT, HS256) in the body. The refresh token (256 random bits, stored only as a hash) lives in an HttpOnly, Secure, SameSite=Strict cookie and rotates on every use. A refresh token that is used twice ends the whole session. `POST /api/auth/refresh`, `/logout`, `/change-password` and `GET /api/auth/me` complete the set.

**Login protection.** Every failure returns the same 401. An account locks for 15 minutes after 5 failures. Failed logins are also counted per client IP (HTTP 429 with `Retry-After`).

**Authorization.** The API is deny-by-default. Every endpoint declares who may call it (`@AdminOnly`, `@StaffOnly`, `@PatientOnly`, `@AnyAuthenticatedUser`, or `@PublicEndpoint`); an architecture test fails the build if one does not.

**Staff accounts** (`/api/admin/staff-accounts`, administrators only). Create, list, view, update, deactivate, activate, unlock and reset the password. The server generates a temporary password that is shown once. Until the user changes it, their access token carries no real role, so the server refuses every role-protected endpoint. The system always keeps one active administrator, and nobody can deactivate or demote themselves. Deactivating, changing the role or resetting the password ends all sessions of that user.

**Audit table.** `audit_event` accepts inserts only; a database trigger rejects UPDATE, DELETE and TRUNCATE.

## Documents

| File | Purpose |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Product requirements, SRS and OOAD design (what and why) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | How the system is built |
| [`docs/PHASES.md`](docs/PHASES.md) | Delivery phases, MVP cut line, risks |
| [`docs/TASKS.md`](docs/TASKS.md) | Task list to work through |
| [`docs/RULES.md`](docs/RULES.md) | Development rules for people and the AI assistant |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Permanent architecture decisions |
| [`docs/MEMORY.md`](docs/MEMORY.md) | Current project state |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | How the system is verified |
| [`docs/SECURITY.md`](docs/SECURITY.md) | Security requirements and checklist |
| [`docs/DESIGN.md`](docs/DESIGN.md) | Visual and UX system |
| [`docs/PROMPTS.md`](docs/PROMPTS.md) | Prompt templates for AI-assisted development |
| `.env.example` | Required configuration names |

Layout: `backend/` (Spring Boot), `frontend/` (Angular), `docs/` (documents above), `ops/` (deployment helpers).

## Quick Start

Requirements: Docker Desktop. For development outside Docker: Java 21 or later, Maven, Node 22.22.3 or later (or 24.15+).

```
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
```

Open `.env` and set `SEED_ADMIN_PASSWORD` (at least 12 characters, a letter and a digit, not containing "admin"). Without it no administrator account is created. Then:

```
docker compose up --build
```

| What | Address |
|---|---|
| Application | http://localhost:8080 |
| Development mail inbox (Mailpit) | http://localhost:8025 |
| PostgreSQL (local tools only) | localhost:5432 (`POSTGRES_PORT` in `.env`) |

Stop with `docker compose down` (data is kept); `docker compose down -v` also deletes the database volume.

### Try the API

The seeded administrator is `admin`, and must change the password at first login:

```
# 1. Sign in (the token only works for /api/auth/me and /api/auth/change-password for now)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<SEED_ADMIN_PASSWORD>"}'

# 2. Change the password with that token; the response holds a full admin token
curl -s -X POST http://localhost:8080/api/auth/change-password \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"currentPassword":"<SEED_ADMIN_PASSWORD>","newPassword":"<new password>"}'

# 3. List staff accounts
curl -s http://localhost:8080/api/admin/staff-accounts -H "Authorization: Bearer <new accessToken>"
```

## Tests

```
cd backend  && mvn clean test     # needs Docker running (Testcontainers)
cd frontend && npm install && npm test -- --watch=false && npm run build
```

## Roles

PATIENT, DOCTOR, NURSE, PHARMACIST, RECEPTIONIST, LAB_TECHNICIAN, ADMIN.

## Known limits

- Login rate limiting is kept in the memory of one backend process, which is right for the single backend of the Compose setup; several instances would need a shared store.
- The audit table is append-only through a database trigger. The application still connects as the table owner, so a stricter deployment would use separate migration and application database roles.
- An access token stays valid up to 15 minutes after its user is deactivated; the refresh then fails.

## Contributing

Read `docs/RULES.md` first. Work one task at a time from `docs/TASKS.md`, with tests, and update `docs/MEMORY.md` after each task.
