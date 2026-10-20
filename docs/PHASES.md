# Phases: CareConnect Delivery Plan

Sizes are effort estimates for one developer working with an AI assistant, not calendar promises: **S** ≈ 2–3 working days, **M** ≈ about a week, **L** ≈ about two weeks. Calendar dates are added once the deadline is fixed (open item 1 in `PRD.md`, deadline not yet given).

## 1. Overview

| Phase | Name | Size | Delivers | Requirements |
|---|---|---|---|---|
| 0 | Foundation and documents | S | Repo, Docker Compose, CI, skeletons, approved docs | none (setup) |
| 1 | Identity, access and audit | M | Login, roles, staff accounts, audit capture | FR-01, 02, 03, 37 |
| 2 | Patient registry | M | Register, search, demographics, allergies, problems, chart header | FR-05 to FR-10 |
| 3 | Scheduling and encounters | M | Availability, booking, cancel, check-in, encounter | FR-11 to FR-14 |
| 4 | Clinical documentation | M | Vitals, notes, signing, addenda, diagnoses, timeline | FR-15 to FR-19 |
| 5 | Medication ordering and safety | L | Formulary, rules, CPOE, safety engine, overrides, lifecycle | FR-20 to FR-23, FR-26 |
| 6 | Pharmacy and medication management | M | Verification, dispensing, active list, refill decisions, MAR | FR-27 to FR-31 |
| 7 | Lab orders and results | S | Lab catalogue, orders, results, release | FR-24, FR-25 |
| 8 | Patient portal | M | Activation, summary, appointments, results, notes, notifications, messaging | FR-04, FR-32 to FR-36 |
| 9 | Audit review, reports and hardening | M | Audit search, reports, break-glass, security and accessibility pass | FR-38 to FR-40 |
| 10 | Release and handover | S | Seed data, deployment, demo script, final documents | none (delivery) |

Every one of the 40 functional requirements is assigned to exactly one phase above.

## 2. MVP Cut Line

| Level | Contents |
|---|---|
| **MVP** | All M requirements: Phases 0–7 (including lab FR-24, FR-25 and the medication administration record FR-30), plus portal items FR-32 and FR-33 from Phase 8, plus FR-38 from Phase 9 |
| **Full v1** | All S requirements: FR-04, 11, 19, 28, 31, 34, 36, 40 |
| **Stretch** | C requirements: FR-35, FR-39 |

If time runs short, cut in this order: C items, then FR-40 and FR-36, then FR-19. Lab (Phase 7) and the medication administration record are confirmed requirements and are not cut. Never cut: safety checks, signing immutability, authorization, audit.

## 3. Phase Details

### Phase 0: Foundation and Documents (S)

- **Goal:** a working skeleton and agreed documents before any feature.
- **Work:** repository layout; Spring Boot and Angular skeletons; Docker Compose with PostgreSQL and Mailpit; Flyway baseline; ArchUnit rule skeleton; CI pipeline; `.env.example`; mentor review of `PRD.md`.
- **Exit criteria:** `docker compose up` starts everything; empty health endpoint and Angular home page respond; CI runs green; PRD approved.

### Phase 1: Identity, Access and Audit (M)

- **Goal:** secure foundation every later module relies on.
- **Work:** users, roles, login and refresh, lockout, RBAC with method security, staff account management, `PatientAccessPolicy` skeleton, audit capture (aspect, store, annotation), Angular login, guards, interceptor and role-based shells.
- **Exit criteria:** TC-01 to TC-07 and TC-72 to TC-74 pass; authorization matrix test exists for current endpoints; every protected endpoint rejects the wrong role.
- **Why first:** retrofitting security and audit later is the most common and most dangerous shortcut.

### Phase 2: Patient Registry (M)

- **Goal:** the patient chart foundation.
- **Work:** patient registration with MRN generation, duplicate warning, search, demographics update with before/after audit, allergies, problem list with ICD-10 subset, chart header and summary (allergies, problems; medications and vitals fill in later).
- **Exit criteria:** TC-10 to TC-19 pass (TC-19 fully after Phases 4 and 6); chart opens and every open is audited.

### Phase 3: Scheduling and Encounters (M)

- **Goal:** the visit workflow starts.
- **Work:** provider availability, slot generation, booking, double-booking protection, reschedule, cancel with late flag, check-in, encounter creation, receptionist screens.
- **Exit criteria:** TC-20 to TC-27 pass; concurrency test proves two simultaneous bookings cannot both succeed.

### Phase 4: Clinical Documentation (M)

- **Goal:** trustworthy documentation.
- **Work:** vitals with BMI, note templates, draft saving, signing with immutability, addenda, entered-in-error handling, diagnoses, encounter timeline, doctor and nurse screens.
- **Exit criteria:** TC-28 to TC-36 pass; direct attempts to change a signed note through the API are rejected.

### Phase 5: Medication Ordering and Safety (L)

- **Goal:** the core of the project, correct and demonstrable.
- **Work:** formulary and interaction rule management (FR-26), order draft, safety engine with four rules, alert UI, override with reason, contraindicated and duplicate blocks, signing with snapshot, discontinue, lifecycle state machine.
- **Exit criteria:** TC-37 to TC-48 and TC-52, TC-53 pass; every rule has unit tests for hit, miss and edge cases; the project owner reviews the demo rule values; re-check at signing time is proven by a test where an allergy is added between draft and sign.
- **Risk:** largest phase; protect time for it.

### Phase 6: Pharmacy and Medication Management (M)

- **Goal:** close the medication loop.
- **Work:** verification queue, verify and reject with reason, dispensing, active medication list, refill decision flow (request API used by the portal later), medication administration record, chart header updated with active medications.
- **Exit criteria:** TC-54 to TC-63 pass including TC-59 and TC-60 (MAR); full journey from signed order to dispensed works end to end.

### Phase 7: Lab Orders and Results (S)

- **Goal:** second order type and the release-to-patient rule.
- **Work:** lab test catalogue, lab order, result entry with flags, provider review and release.
- **Exit criteria:** TC-49 to TC-51 pass.

### Phase 8: Patient Portal (M)

- **Goal:** patient-facing side, strictly isolated.
- **Work:** one-time activation code flow, portal summary, own appointments (book and cancel), released results, shared notes, refill request screen, notifications and email through Mailpit, secure messaging (stretch).
- **Exit criteria:** TC-8, TC-9, TC-64 to TC-71 pass; a dedicated test proves a patient cannot read another patient's data by changing identifiers.

### Phase 9: Audit Review, Reports and Hardening (M)

- **Goal:** governance and quality.
- **Work:** audit search screen, reports (appointments, orders, overrides), break-glass (stretch), full authorization matrix, dependency scan, security headers, accessibility pass (keyboard, contrast, labels), performance check against NFR-01, Playwright end-to-end journeys, bug fixing.
- **Exit criteria:** TC-75 to TC-80 pass; full end-to-end suite green; security checklist in `SECURITY.md` complete; no open critical or high bugs.

### Phase 10: Release and Handover (S)

- **Goal:** something a mentor can run and judge.
- **Work:** deterministic synthetic seed data, demo accounts for every role, deployment via Docker Compose, backup and restore rehearsal, demo script and screenshots, final updates to `PRD.md`, `ARCHITECTURE.md`, `README.md`, `MEMORY.md`.
- **Exit criteria:** a fresh machine goes from clone to working demo using only the README; demo script runs without surprises.

## 4. Feature Delivery Loop (applies to every task)

```
READ docs → UNDERSTAND → PLAN → IMPLEMENT → TEST → REVIEW → FIX → COMMIT → UPDATE DOCS
```

**Definition of done for a task:** acceptance criteria met; unit and integration tests added and passing; lint and type checks pass; authorization and audit verified for any endpoint touched; reviewed against `PRD.md`, `ARCHITECTURE.md` and `SECURITY.md`; small commit with a clear message; `TASKS.md` and `MEMORY.md` updated.

**Vertical slices:** within a phase, build one complete user flow at a time (screen, API, service, database, tests), not all screens first.

## 5. Dependencies

```
Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
                                              └──────────→ Phase 7
Phase 6 → Phase 8 → Phase 9 → Phase 10
```

Phase 7 can run any time after Phase 4. Phase 8 needs Phases 3, 6 and 7 for its content.

## 6. Risk Register

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Scope creep across five large modules | Nothing finished | MoSCoW priorities; fixed cut line; changes need a PRD update first |
| R2 | AI invents incorrect clinical rules | Wrong safety behavior | Rules only from PRD and DECISIONS; owner reviews demo rule data; rule unit tests |
| R3 | Security retrofitted late | Data exposure | Phase 1 builds access and audit first; authorization matrix tests from the start |
| R4 | Audit write fails or is bypassed | Unaudited access to patient data | Audit in the same transaction; fail-closed policy; integration test for audit failure; DB-level append-only protection |
| R5 | AI-generated code breaks working features | Regressions | Small tasks, tests per task, small commits, branches |
| R6 | Phase 5 overruns | Delay of everything after it | Start it with the safety engine and unit tests; protect time, cut only S and C items |
| R7 | Mentor requires Oracle later | Rework of migrations and JSONB columns | Portable JPA and Flyway, JSONB limited to two columns; switch only on written mandate |
| R8 | Time lost to environment and setup issues | Early delay | Phase 0 is its own phase with an exit test |

## 7. Review Gates

- **After Phase 0:** mentor reviews the PRD and open items.
- **After Phase 2:** demo of chart and audit.
- **After Phase 5:** demo of safety checks with the project owner reviewing rule values.
- **After Phase 6:** end-to-end medication journey demo.
- **After Phase 9:** full demonstration run and checklist review.
