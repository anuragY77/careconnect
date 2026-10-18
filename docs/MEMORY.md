# Project Memory: CareConnect

Current state of the project. Update after every task. Permanent decisions are in `DECISIONS.md`.

## Current Status
Planning and documentation. No code written yet.

## Completed
- TASK-002, TASK-003: repo at /home/claude/careconnect (backend, frontend, docs, ops), docs moved into docs/, git initialised on main
- `PRD.md` first draft (SRS and OOAD format, 40 requirements, 34 use cases, diagrams, data model, traceability)
- `ARCHITECTURE.md`, `PHASES.md`, `RULES.md`, `TASKS.md`, `DECISIONS.md`, `TEST_PLAN.md`, `SECURITY.md`, `DESIGN.md`, `PROMPTS.md`, `README.md`, `.env.example` drafted

## Current Task
TASK-004: Spring Boot skeleton (TASK-001 PRD review with mentor still pending on owner's side; owner told to proceed).

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
