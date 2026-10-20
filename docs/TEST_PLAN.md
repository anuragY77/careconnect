# Test Plan: CareConnect

Defines what "working" means. Test case ids (TC-xx) match the traceability matrix in `PRD.md` section 6.1.

## 1. Strategy

Test continuously, close to the code. Every task adds tests for its important behavior including failure paths. Safety, authorization, immutability and audit get the deepest coverage because they carry the most risk.

```
Unit → Integration → Architecture → End-to-end → Security and accessibility
```

## 2. Test Levels and Tools

| Level | Tools | Scope |
|---|---|---|
| Unit (backend) | JUnit 5, Mockito | Services, domain state transitions, each safety rule |
| Integration (backend) | Spring Boot Test, Testcontainers (PostgreSQL) | Repositories, Flyway migrations, controllers with real security |
| Architecture | ArchUnit | Layer and module boundary rules |
| Unit (frontend) | Angular test runner | Services, guards, interceptor, key components |
| End-to-end | Playwright | Core user journeys in the browser |
| Security | Authorization matrix tests, dependency scan | Role × endpoint, object-level access |
| Accessibility | Automated audit plus manual keyboard check | Core screens |

## 3. Targets

- Service-layer line coverage at least 70%; safety rules and state machines aim for full branch coverage.
- Zero known critical or high defects at release.
- All M requirements have at least one passing automated test per TC listed in the matrix.

## 4. Test Data

- Synthetic only. A deterministic seed script creates demo patients, staff for every role, a formulary, interaction rules and a lab catalogue.
- Tests build their own data through builders; they do not depend on seed data.
- Never use real names, real identifiers or real clinical content.

## 5. Critical Scenarios

### 5.1 Medication safety
- Allergy match (SEVERE) produces an overridable alert; override requires a reason of at least 10 characters.
- Contraindicated interaction blocks signing and offers no override.
- Duplicate active order blocks signing.
- Dose above per-dose or daily maximum produces an alert.
- Allergy added between draft and sign is caught at signing.
- Safety snapshot and overrides are stored and audited.
- Invalid state transitions are rejected (for example dispensing an unverified order).

### 5.2 Clinical documentation
- Signed note cannot be changed through any endpoint; addendum works.
- Signing without a primary diagnosis fails.
- Concurrent edits to a draft produce a lock conflict, not silent overwrite.

### 5.3 Scheduling
- Two simultaneous bookings of one slot: exactly one succeeds.
- Check-in on a cancelled appointment fails.

### 5.4 Portal isolation
- A patient token with another patient's id in the path, query or body is refused and the attempt is audited.
- Unreleased lab results and unshared notes never appear for the patient.

### 5.5 Audit
- Chart view, update, sign, verify, override and failed login each produce an audit event with user, role, time, patient and outcome.
- When the audit store is unavailable, the operation fails (fail-closed).
- Application user cannot update or delete audit events.

## 6. Authorization Matrix

Automated test for every endpoint: allowed roles succeed, all other roles and anonymous users are refused. Minimum expectations:

| Capability | PATIENT | DOCTOR | NURSE | PHARMACIST | RECEPTIONIST | LAB_TECH | ADMIN |
|---|---|---|---|---|---|---|---|
| Register or search patients | No | Yes | Yes | No | Yes | No | No |
| View chart | Own only | Yes | Yes | Limited | Limited | No | No |
| Record vitals | No | Yes | Yes | No | No | No | No |
| Sign note or order | No | Yes | No | No | No | No | No |
| Verify or dispense | No | No | No | Yes | No | No | No |
| Enter lab result | No | No | No | No | No | Yes | No |
| Manage formulary | No | No | No | Yes | No | No | Yes |
| Manage staff accounts | No | No | No | No | No | No | Yes |
| Review audit trail | No | No | No | No | No | No | Yes |

This matrix is reviewed by the project owner and adjusted in `PRD.md` first, then in tests.

## 7. End-to-End Journeys (Playwright)

1. Receptionist registers a patient, books and checks in; encounter opens.
2. Nurse records vitals; doctor writes and signs a note; addendum added.
3. Doctor orders a medication with an allergy alert, overrides with a reason; pharmacist verifies and dispenses; medication shows as active.
4. Doctor attempts a contraindicated combination and is blocked.
5. Patient activates the portal, views own summary, books and cancels an appointment, requests a refill; doctor approves.
6. Administrator searches the audit trail and finds the events from journeys 1 to 5.
7. Cross-role denial checks: wrong role gets refused on direct URL access.

## 8. Responsive and Accessibility

- Test at 375 px, 768 px and 1440 px.
- Keyboard-only navigation of login, patient search, order entry and portal summary.
- Form labels, error messages linked to fields, visible focus, contrast at WCAG 2.1 AA, alerts not conveyed by color alone.

## 9. Performance Check

- Seed at least 10,000 synthetic patients; patient search under 2 seconds and summary under 3 seconds (NFR-01).

## 10. Entry and Exit Criteria

- **Entry for a task:** acceptance criteria written, related TCs identified.
- **Exit for a task:** its tests pass; lint, type check and build pass; no new critical findings.
- **Exit for release:** all journeys pass on the demo environment; security checklist complete; backup and restore rehearsed once.

## 11. Defect Handling

Each defect records steps to reproduce, expected and actual behavior, severity, and the failing test that proves the fix. A defect is closed only after a regression test exists.
