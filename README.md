# CareConnect: Patient–Provider EHR

A web-based Electronic Health Record system covering patient records, clinical documentation, computerized provider order entry (CPOE), medication management and a patient portal.

> **Training and demonstration project.** It uses synthetic data only and demo clinical rules. It must not be used for real patient care.

## Status

Planning and documentation complete in draft. No application code yet. See `MEMORY.md` for the current state.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot |
| Frontend | Angular, Angular Material |
| Primary database | PostgreSQL |
| Audit trail | PostgreSQL (append-only table) |
| Runtime | Docker Compose |
| Testing | JUnit 5, Testcontainers, ArchUnit, Playwright |

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

## Quick Start (planned, available after Phase 0)

```
cp .env.example .env      # fill in local values
docker compose up --build
```

Then open the frontend at the address shown by the compose output. Demo accounts for each role are created by the seed profile in Phase 10.

## Roles

PATIENT, DOCTOR, NURSE, PHARMACIST, RECEPTIONIST, LAB_TECHNICIAN, ADMIN.

## Contributing

Read `RULES.md` first. Work one task at a time from `TASKS.md`, with tests, and update `MEMORY.md` after each task.
