-- A baby born just after midnight in India is still "yesterday" in UTC, which is the time zone the
-- database and the application run in (ADR-015). Allow a date of birth one day ahead of the UTC date.
ALTER TABLE patient DROP CONSTRAINT ck_patient_dob;
ALTER TABLE patient ADD CONSTRAINT ck_patient_dob
    CHECK (dob <= CURRENT_DATE + 1 AND dob >= DATE '1900-01-01');
