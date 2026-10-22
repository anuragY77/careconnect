# Tasks: CareConnect

Work top to bottom. One task at a time: understand, plan, implement, test, review, commit, update docs.
Phases match `PHASES.md`. Phases 0 to 2 are broken down in detail; later phases are listed at feature level and are expanded into small tasks when the phase starts.

Legend: `[ ]` open, `[x]` done. Requirement ids refer to `PRD.md`.

## Phase 0: Foundation and Documents

- [ ] TASK-001 Review and approve `PRD.md` with the mentor; record open items answers in `DECISIONS.md`
- [x] TASK-002 Create repository layout: `backend/`, `frontend/`, `docs/`, root `README.md`, `.gitignore`
- [x] TASK-003 Move documents into `docs/` and add `RULES.md`, `TASKS.md`, `MEMORY.md` links in README
- [x] TASK-004 Generate Spring Boot skeleton (Java LTS, Maven, Web, Security, Validation, JPA, PostgreSQL, Actuator, Flyway)
- [x] TASK-005 Generate Angular skeleton with Angular Material and routing
- [x] TASK-006 Create Docker Compose: postgres, mailpit, backend, frontend
- [x] TASK-007 Add `.env.example` and load configuration from environment
- [x] TASK-008 Add Flyway baseline migration and verify it runs on startup
- [ ] TASK-009 Add base error handling: RFC 7807 responses and a stable error code enum
- [ ] TASK-010 Add ArchUnit tests for layering and module boundaries (initially trivial, extended as modules appear)
- [ ] TASK-011 Add CI workflow: build, lint, unit and integration tests
- [ ] TASK-012 Add health endpoint and Angular home page; confirm `docker compose up` works from a clean clone

## Phase 1: Identity, Access and Audit (FR-01, FR-02, FR-03, FR-37)

- [ ] TASK-013 Migration: `app_user` table; role and status enums
- [ ] TASK-014 Password hashing and user entity; seed one admin account for local use
- [x] TASK-015 Login endpoint with lockout after 5 failures (TC-01, TC-02)
- [x] TASK-015a Rate limiting on login and password-spraying protection: per-IP failed-login limit, HTTP 429 with Retry-After (ADR-016)
- [ ] TASK-016 (reject the placeholder `JWT_SECRET` from `.env.example` outside the local profile) JWT access token and refresh token with rotation and HttpOnly cookie (TC-03)
- [ ] TASK-017 Security configuration: stateless, CORS, headers, public and protected routes
- [ ] TASK-018 Method security and role checks; first authorization matrix test (TC-04)
- [ ] TASK-019 Staff account management API: create, update, deactivate, reset (TC-06, TC-07)
- [ ] TASK-020 `audit_event` migration (append-only: INSERT and SELECT grants, trigger blocking UPDATE and DELETE) and `AuditStore` interface with PostgreSQL implementation
- [ ] TASK-021 `@Audited` annotation, audit aspect and login audit including failures (TC-74)
- [ ] TASK-022 Fail-closed behavior test: audit failure fails the request
- [ ] TASK-023 Angular login page, auth service, token handling and refresh interceptor
- [ ] TASK-024 Role guards and the two shells (`/staff`, `/portal`) with role-based navigation
- [ ] TASK-025 Admin screen: staff accounts list and form

## Phase 2: Patient Registry (FR-05 to FR-10)

- [ ] TASK-026 Migration: `patient`, `allergy`, `problem` tables with constraints and indexes
- [ ] TASK-027 MRN generation (unique, immutable) and registration endpoint (TC-10)
- [ ] TASK-028 Duplicate patient warning (TC-11)
- [ ] TASK-029 Patient search endpoint with pagination (TC-12, TC-13)
- [ ] TASK-030 Demographics update with before and after audit (TC-14)
- [ ] TASK-031 Allergy endpoints: add, mark inactive or entered-in-error; no delete (TC-15, TC-16)
- [ ] TASK-032 Problem list endpoints with ICD-10 subset lookup (TC-17, TC-18)
- [ ] TASK-033 `PatientAccessPolicy` and chart-view audit interceptor (BR-07, BR-12)
- [ ] TASK-034 Patient summary service (allergies, problems; medications and vitals added later) (TC-19)
- [ ] TASK-035 Angular: patient registration form
- [ ] TASK-036 Angular: patient search and results list
- [ ] TASK-037 Angular: chart header, allergies panel, problems panel
- [ ] TASK-038 Authorization matrix test extended for all patient endpoints

## Phase 3: Scheduling and Encounters (FR-11 to FR-14)

- [ ] TASK-039 Migration and API: provider availability (TC-20, TC-21)
- [ ] TASK-040 Slot generation and listing
- [ ] TASK-041 Book appointment with double-booking protection and concurrency test (TC-22, TC-23)
- [ ] TASK-042 Reschedule and cancel with late-cancellation flag (TC-24, TC-25)
- [ ] TASK-043 Check-in opens encounter (TC-26, TC-27)
- [ ] TASK-044 Angular: scheduling calendar, booking and check-in screens

## Phase 4: Clinical Documentation (FR-15 to FR-19)

- [ ] TASK-045 Vitals API with BMI and range validation (TC-28, TC-29) and Angular form
- [ ] TASK-046 Clinical note draft API with templates and optimistic locking (TC-30, TC-31)
- [ ] TASK-047 Sign note, immutability and entered-in-error (TC-32, TC-33)
- [ ] TASK-048 Addendum API (TC-34)
- [ ] TASK-049 Diagnoses on encounter; signing requires a primary diagnosis (TC-35)
- [ ] TASK-050 Encounter timeline (TC-36)
- [ ] TASK-051 Angular: note editor, signing flow, addendum, timeline

## Phase 5: Medication Ordering and Safety (FR-20 to FR-23, FR-26)

- [ ] TASK-052 Migrations and API: drug formulary and interaction rules (TC-52, TC-53)
- [ ] TASK-053 Seed demo formulary and rules (reviewed by the project owner)
- [ ] TASK-054 Medication order entity, draft API, domain state machine (TC-37, TC-38, TC-48)
- [ ] TASK-055 `SafetyRule` interface and `SafetyCheckService`
- [ ] TASK-056 `AllergyRule` with unit tests (TC-39)
- [ ] TASK-057 `DuplicateTherapyRule` with unit tests (TC-40)
- [ ] TASK-058 `InteractionCheckRule` with unit tests (TC-41)
- [ ] TASK-059 `DoseRangeRule` with unit tests (TC-42)
- [ ] TASK-060 Sign endpoint: re-run checks, store snapshot, overrides and blocks (TC-43 to TC-46)
- [ ] TASK-061 Test: allergy added between draft and sign is caught
- [ ] TASK-062 Discontinue order with reason (TC-47)
- [ ] TASK-063 Angular: order entry, alert panel, override dialog
- [ ] TASK-064 Angular: formulary and rules admin screen

## Phase 6: Pharmacy and Medication Management (FR-27 to FR-31)

- [ ] TASK-065 Pharmacy queue API and verify and reject (TC-54, TC-55)
- [ ] TASK-066 Dispensing record (TC-56, TC-57)
- [ ] TASK-067 Active medication list; add to patient summary (TC-58)
- [ ] TASK-068 Refill request and doctor decision API (TC-61 to TC-63)
- [ ] TASK-069 Angular: pharmacy queue and order review screen
- [ ] TASK-070 Angular: active medications panel and refill decision list
- [ ] TASK-071 Medication administration record API and screen (TC-59, TC-60)

## Phase 7: Lab Orders and Results (FR-24, FR-25)

- [ ] TASK-072 Lab catalogue, lab order API and screen (TC-49)
- [ ] TASK-073 Result entry with flags, review and release (TC-50, TC-51)

## Phase 8: Patient Portal (FR-04, FR-32 to FR-36)

- [ ] TASK-074 Portal activation codes and activation flow (TC-8, TC-9)
- [ ] TASK-075 Portal summary and own-data isolation tests (TC-5, TC-64)
- [ ] TASK-076 Portal appointments: view, book, cancel (TC-65)
- [ ] TASK-077 Released results and shared notes (TC-66, TC-67)
- [ ] TASK-078 Portal refill request screen
- [ ] TASK-079 Notifications and email through Mailpit (TC-70, TC-71)
- [ ] TASK-080 (Stretch) Secure messaging (TC-68, TC-69)

## Phase 9: Audit Review, Reports and Hardening (FR-38 to FR-40)

- [ ] TASK-081 Audit search API and screen (TC-75, TC-76)
- [ ] TASK-082 Reports: appointments, orders, overrides (TC-79, TC-80)
- [ ] TASK-083 (Stretch) Break-glass access (TC-77, TC-78)
- [ ] TASK-084 Complete authorization matrix across all endpoints
- [ ] TASK-085 Dependency vulnerability scan and security header check
- [ ] TASK-086 Accessibility pass on core screens
- [ ] TASK-087 Performance check against NFR-01
- [ ] TASK-088 Playwright journeys: registration to dispensing; portal isolation; audit review
- [ ] TASK-089 Bug-fix sweep and security checklist sign-off

## Phase 10: Release and Handover

- [ ] TASK-090 Synthetic seed data and demo accounts for every role
- [ ] TASK-091 Demo deployment with Docker Compose; backup and restore rehearsal
- [ ] TASK-092 Demo script, screenshots and README quick start verified from a clean machine
- [ ] TASK-093 Final update of `PRD.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `MEMORY.md`
