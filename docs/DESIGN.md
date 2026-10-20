# Design System: CareConnect

Gives every screen a consistent look and behavior. Clinical software prioritizes clarity, speed and safety over decoration.

## 1. Principles

- Calm, clean, professional. Information density is acceptable for staff screens; the patient portal is simpler and roomier.
- Safety information (allergies, alerts) is always visible and impossible to miss.
- Never rely on color alone: pair color with an icon and text.
- Every screen has loading, empty, error and success states.

## 2. Foundation

- Component library: Angular Material, with a single custom theme.
- Typography: Inter (fall back to Roboto, then system sans-serif). Base 16 px; headings 24, 20, 18; table text 14.
- Spacing scale: 4, 8, 12, 16, 24, 32 px.
- Corner radius: 8 px for controls, 12 px for cards.

## 3. Colors

| Role | Value |
|---|---|
| Primary | #0F766E (teal) |
| Primary dark | #115E59 |
| Background | #F8FAFC |
| Surface | #FFFFFF |
| Text | #0F172A |
| Muted text | #475569 |
| Border | #E2E8F0 |
| Info | #1D4ED8 |
| Success | #15803D |
| Warning | #B45309 |
| Critical | #B91C1C |

All text and control colors must meet WCAG 2.1 AA contrast. Dark mode is out of scope for v1.

## 4. Clinical Alert Component

| Level | Icon | Label | Behavior |
|---|---|---|---|
| INFO | info circle | Information | Dismissible |
| WARNING | triangle | Warning | Needs acknowledgment; may need override reason |
| CRITICAL | octagon | Critical | Blocks signing; no override when not overridable |

Each alert shows what was found, why it matters, and what the user can do next. Overrides open a dialog with a required reason field.

## 5. Layouts

- **Staff shell:** top bar (user, role, notifications, logout), left navigation by role, main content. Patient chart pages show a sticky **chart header** with name, age, sex, MRN and an allergies badge.
- **Portal shell:** simple top bar, large cards for summary, appointments, medications and results.
- Responsive at 375 px, 768 px and 1440 px. Staff screens are optimized for desktop and tablet; the portal for phone first.

## 6. Components

- Buttons: Primary, Secondary, Destructive (confirmation required), Text.
- Forms: visible labels, inline validation, errors linked to fields, submit disabled while saving.
- Tables: sortable, paginated, keyboard accessible, empty state message.
- Dialogs: confirm destructive or irreversible actions (signing, discontinuing, cancelling) and state the consequence.
- Status chips: appointment, order and note statuses use consistent colors plus text.
- Notifications: toast for confirmations; persistent banner for errors that need action.

## 7. UX Requirements

- Mobile responsive; loading, empty and error states everywhere.
- Accessible forms and full keyboard navigation with visible focus.
- Irreversible actions (sign note, sign order) show a clear review step first.
- Timeouts: session expiry shows a clear message and returns the user to where they were after login.
- A fixed footer or banner states that the system is for training and uses synthetic data.

## 8. Content Style

- Plain language, short labels, consistent terms that match `PRD.md` (Patient, Encounter, Medication order, Addendum).
- Error messages say what went wrong and how to fix it, without internal details.
