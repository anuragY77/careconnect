-- Patient registry: patient, allergy and problem list (FR-05 to FR-09, NFR-10).
-- Clinical rows are never deleted (BR-08): a trigger rejects DELETE, and corrections use a status
-- with a reason. The MRN is generated from a sequence by the application and can never change.

CREATE SEQUENCE patient_mrn_seq START WITH 100001;

CREATE TABLE patient (
    patient_id               BIGSERIAL PRIMARY KEY,
    mrn                      VARCHAR(20)  NOT NULL,
    first_name               VARCHAR(80)  NOT NULL,
    last_name                VARCHAR(80)  NOT NULL,
    dob                      DATE         NOT NULL,
    sex                      VARCHAR(10)  NOT NULL,
    phone                    VARCHAR(20),
    email                    VARCHAR(254),
    address                  VARCHAR(300),
    emergency_contact_name   VARCHAR(120),
    emergency_contact_phone  VARCHAR(20),
    insurance_provider       VARCHAR(120),
    insurance_policy_number  VARCHAR(60),
    status                   VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_patient_mrn UNIQUE (mrn),
    CONSTRAINT ck_patient_sex CHECK (sex IN ('FEMALE', 'MALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT ck_patient_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_patient_dob CHECK (dob <= CURRENT_DATE AND dob >= DATE '1900-01-01'),
    CONSTRAINT ck_patient_names CHECK (btrim(first_name) <> '' AND btrim(last_name) <> '')
);

-- Search by name, phone and date of birth (FR-06).
CREATE INDEX ix_patient_last_name ON patient (lower(last_name), lower(first_name));
CREATE INDEX ix_patient_phone ON patient (phone);
CREATE INDEX ix_patient_dob ON patient (dob);

CREATE TABLE allergy (
    allergy_id    BIGSERIAL PRIMARY KEY,
    patient_id    BIGINT       NOT NULL REFERENCES patient (patient_id),
    substance     VARCHAR(120) NOT NULL,
    -- Link to the drug formulary; the formulary table (and this foreign key) arrive with Phase 5.
    drug_id       BIGINT,
    reaction      VARCHAR(200),
    severity      VARCHAR(10)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(200),
    recorded_by   BIGINT       NOT NULL REFERENCES app_user (user_id),
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_allergy_severity CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE')),
    CONSTRAINT ck_allergy_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ENTERED_IN_ERROR')),
    -- Leaving ACTIVE needs a written reason.
    CONSTRAINT ck_allergy_status_reason CHECK (
        status = 'ACTIVE' OR (status_reason IS NOT NULL AND btrim(status_reason) <> '')),
    CONSTRAINT ck_allergy_substance CHECK (btrim(substance) <> '')
);

CREATE INDEX ix_allergy_patient_status ON allergy (patient_id, status);

CREATE TABLE problem (
    problem_id    BIGSERIAL PRIMARY KEY,
    patient_id    BIGINT       NOT NULL REFERENCES patient (patient_id),
    icd10_code    VARCHAR(10)  NOT NULL,
    description   VARCHAR(200) NOT NULL,
    onset_date    DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(200),
    recorded_by   BIGINT       NOT NULL REFERENCES app_user (user_id),
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_problem_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'ENTERED_IN_ERROR')),
    CONSTRAINT ck_problem_status_reason CHECK (
        status <> 'ENTERED_IN_ERROR' OR (status_reason IS NOT NULL AND btrim(status_reason) <> '')),
    CONSTRAINT ck_problem_icd10 CHECK (icd10_code ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$'),
    CONSTRAINT ck_problem_description CHECK (btrim(description) <> '')
);

CREATE INDEX ix_problem_patient_status ON problem (patient_id, status);

-- The portal account now has to point at a real patient (open since V2).
ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_linked_patient FOREIGN KEY (linked_patient_id) REFERENCES patient (patient_id);

-- Clinical data is never deleted (BR-08, NFR-10).
CREATE FUNCTION reject_clinical_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are never deleted: use a status with a reason instead', TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_patient_no_delete BEFORE DELETE ON patient
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_delete();
CREATE TRIGGER trg_allergy_no_delete BEFORE DELETE ON allergy
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_delete();
CREATE TRIGGER trg_problem_no_delete BEFORE DELETE ON problem
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_delete();

-- The MRN identifies the patient for life.
CREATE FUNCTION reject_mrn_change() RETURNS trigger AS $$
BEGIN
    IF NEW.mrn IS DISTINCT FROM OLD.mrn THEN
        RAISE EXCEPTION 'the MRN of a patient can never change'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_patient_mrn_immutable BEFORE UPDATE ON patient
    FOR EACH ROW EXECUTE FUNCTION reject_mrn_change();
