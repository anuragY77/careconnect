# Security Requirements: CareConnect

Security is designed in from Phase 1, not added before the demo. This is a training system with synthetic data, but it is built as if the data were real.

## 1. Principles

- Least privilege: every role sees and does only what it needs.
- Server-side enforcement: the UI hides things, the server decides.
- Defense in depth: validation, authorization, audit and database constraints each stand alone.
- Fail closed: if a check or the audit store fails, the operation is refused.
- Synthetic data only, in every environment.

## 2. Authentication

- Passwords hashed with BCrypt (cost 12) or Argon2; minimum length and basic complexity enforced; never logged.
- Five failed logins lock the account for 15 minutes; every failure is audited.
- JWT access token valid 15 minutes, held in memory only.
- Refresh token is opaque, stored hashed, rotated on every use, sent as an HttpOnly, Secure, SameSite=Strict cookie; logout revokes it.
- Portal activation uses a one-time code, stored hashed, with expiry and attempt limits.

## 3. Authorization

- Role checks with method security on service methods, plus controller rules.
- Object-level checks: a PATIENT can only access the patient id inside their token, never an id from the request (BR-07).
- Every endpoint has an authorization test: allowed roles pass, others are refused (see `TEST_PLAN.md` section 6).
- Break-glass access (stretch) requires a reason and is flagged for review.

## 4. Input and Output

- Jakarta Bean Validation on every request DTO; length, range and format limits.
- Parameterized queries only; no string-built SQL.
- Responses use DTOs; entities and internal fields never leave the server.
- Error responses never include stack traces or internal details.
- Output encoding is handled by Angular; no raw HTML rendering of user content.

## 5. Data Protection

- TLS for all traffic outside local development; HSTS enabled.
- Database connections use private networks; PostgreSQL is not exposed publicly.
- Sensitive fields (phone, address, email) are masked in lists and shown in full only where needed.
- No hard deletes of clinical data (BR-08).
- Backups documented and rehearsed once; backup files are treated as sensitive.

## 6. Audit

- Every PHI read and write is audited: user, role, time, entity, patient, outcome, IP.
- Audit store is append-only for the application account (insert and find only).
- Audit failure fails the request (ADR-010).
- Audit contents avoid clinical free text beyond identifiers and before/after values required for demographic changes.

## 7. Secrets and Configuration

- No secrets in source control. `.env.example` lists names only; `.env` is git-ignored.
- JWT signing secret is long, random and different per environment.
- Separate database credentials per environment; the audit user has restricted rights.
- Rotation procedure documented for the JWT secret.

## 8. HTTP and Browser Security

- CORS limited to the frontend origin.
- Content-Security-Policy, X-Content-Type-Options, frame protection and referrer policy set.
- Rate limiting on login and portal activation endpoints.
- CSRF risk is addressed by SameSite=Strict refresh cookie, no cookie-authenticated state-changing endpoints other than refresh, and Authorization-header access tokens.

## 9. Logging

- Structured logs with a correlation id.
- Never log passwords, tokens, note content or patient names; ids only.

## 10. Dependencies and Build

- Dependency vulnerability scan in CI for backend and frontend; high findings block release.
- Pin versions; update on a schedule.
- Container images use minimal base images and run as non-root.

## 11. File Handling

No file uploads are planned in v1. If added, validate type, size and name, store outside the web root, and scan before use.

## 12. Threat Review (summary)

| Threat | Control |
|---|---|
| Stolen or guessed credentials | Hashing, lockout, rate limiting, short tokens, audit of failures |
| Patient reads another patient's data | Token-based patient id, object-level checks, isolation tests, audit of denials |
| Staff views records without need | RBAC, chart-view audit, admin review, break-glass flag |
| Tampering with signed records | Immutability rule, addendum only, audit |
| Tampering with audit trail | Append-only database permissions, restricted user |
| Injection (SQL, script) | Parameterized queries, validation, Angular output encoding, CSP |
| Unsafe medication ordering | Server-side safety engine re-run at signing, non-overridable blocks, stored snapshot |
| Leaked secrets | Environment variables, ignored `.env`, secret scanning in CI |

## 13. Pre-Release Security Checklist

- [ ] No secrets in Git history
- [ ] Authentication, lockout and token refresh verified
- [ ] Authorization matrix tests pass for every endpoint
- [ ] Patient isolation tests pass
- [ ] Audit verified for view, change, sign, verify, override and failed login
- [ ] Audit user cannot update or delete events
- [ ] Input validation present on all requests
- [ ] Security headers verified
- [ ] Dependency scan has no unresolved high findings
- [ ] Logs reviewed for patient names and note content
- [ ] Databases not reachable from outside the host network
- [ ] Backup and restore rehearsed
- [ ] Not-for-clinical-use notice visible in the application
