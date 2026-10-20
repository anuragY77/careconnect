# Development Rules: CareConnect

These rules apply to every contributor, human or AI assistant. Read this file, `PRD.md`, `ARCHITECTURE.md` and `SECURITY.md` before making changes.

## 1. Before Coding

- Read the relevant docs and inspect the existing implementation first.
- Reuse existing code and components before writing new ones.
- For any change touching more than three files, write a short plan first and get it confirmed.
- If a requirement is ambiguous or missing, **stop and ask**. Do not guess.
- Never invent clinical rules, thresholds, drug data or workflows. Clinical behavior comes only from `PRD.md` (business rules BR-xx) and `DECISIONS.md`.

## 2. General

- One task at a time; keep changes small and focused.
- Do not modify unrelated files.
- Keep functions small and names explicit. No duplicated logic.
- No commented-out code, no leftover debug output, no TODO without a task id.
- Every change is traceable to a requirement id (FR-xx) or a task id (TASK-xxx).

## 3. Architecture Rules

- Layering: Controller → Service → Repository. No business logic in controllers or Angular components.
- Modules call each other only through service interfaces, never through another module's repository or entity.
- Entities are never exposed through the API; use DTOs.
- State changes go through domain methods that validate the transition. Never set status fields directly.
- Architecture rules are enforced by ArchUnit tests; do not weaken a test to make a build pass.

## 4. Backend (Java / Spring Boot)

- Constructor injection only. Immutable DTOs (records).
- Validate every request with Jakarta Bean Validation; validate business rules in services.
- `@Transactional` on service methods that change data; keep transactions short.
- Use optimistic locking (`@Version`) on patient, note and order.
- Exceptions: domain exceptions mapped to RFC 7807 responses with a stable `code`. Never return stack traces.
- Never log patient identifiers beyond ids, and never log note content, passwords or tokens.
- Authorization on service methods with `@PreAuthorize`, in addition to controller rules.

## 5. Database

- Schema changes only through new Flyway migrations. Never edit an applied migration.
- Hibernate `ddl-auto` is `validate`.
- Constraints (FK, not null, check, unique) belong in the database.
- No hard deletes of clinical data.
- Timestamps in UTC.

## 6. Frontend (Angular)

- Standalone components; feature folders; lazy-loaded routes.
- Signals for local and feature state; no global store without an ADR.
- Reactive forms; show validation, loading, empty and error states on every screen.
- Role guards are only a convenience. Never rely on the UI to enforce security.
- Follow `DESIGN.md`. Alerts use icon, text and color together.
- Accessibility: labels on inputs, keyboard navigation, visible focus, sufficient contrast.

## 7. Security

- Never commit secrets. Use environment variables and `.env.example`.
- Verify authorization on the server for every operation, including object-level checks (patient isolation).
- Patient id for the PATIENT role always comes from the token.
- Validate all input; use parameterized queries only.
- Audit every PHI read and write (see `ARCHITECTURE.md` section 3.6).
- Only synthetic data, in every environment, always.

## 8. Testing

- Every task adds tests for its important behavior, including the failure paths.
- Safety rules and state transitions need unit tests for hit, miss and edge cases.
- Every new endpoint adds an authorization test: allowed role passes, other roles are refused.
- Run the full relevant checks before committing: lint, type check, unit and integration tests, build.
- Fix failing tests before moving on. Do not skip or disable tests.

## 9. Git

- Branches: `feature/<area>`, `fix/<issue>`, `docs/<topic>`; `main` always builds and passes tests.
- Small commits using conventional messages: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`.
- Never commit generated build output or secrets.
- Merge only after checks pass and the change is reviewed against the docs.

## 10. Working With the AI Assistant

- Start each session by having it read the docs listed at the top and report what it understood, with no code.
- Ask for a plan for the task, review it, then implement.
- Give acceptance criteria and constraints in every request (see `PROMPTS.md`).
- Ask the assistant to report: files changed, what was implemented, tests run, remaining issues.
- When something breaks, give the error, expected behavior, actual behavior and steps to reproduce; ask for the root cause before any fix, then the smallest fix.
- After each task, update `TASKS.md` and `MEMORY.md`; update `DECISIONS.md` when a decision is made.

## 11. Definition of Done

A task is done only when:

1. Acceptance criteria are met.
2. Tests for success and failure paths pass.
3. Lint, type check and build pass.
4. Authorization and audit are verified for any endpoint touched.
5. The change was reviewed against `PRD.md`, `ARCHITECTURE.md` and `SECURITY.md`.
6. The commit is small, with a clear message.
7. `TASKS.md` and `MEMORY.md` are updated.
