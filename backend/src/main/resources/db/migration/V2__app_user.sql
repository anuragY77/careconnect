-- Application users: staff accounts and linked patient portal accounts.
-- Business invariants are enforced here as well as in code (defense in depth).
CREATE TABLE app_user (
    user_id              BIGSERIAL PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    email                VARCHAR(254) NOT NULL,
    role                 VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    linked_patient_id    BIGINT,
    failed_attempts      INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_app_user_role CHECK (role IN
        ('PATIENT', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'ADMIN')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_app_user_failed_attempts CHECK (failed_attempts >= 0),
    -- A patient account is linked to exactly one patient; staff accounts are linked to none.
    -- The foreign key to the patient table is added with the patient registry (Phase 2).
    CONSTRAINT ck_app_user_patient_link CHECK (
        (role = 'PATIENT' AND linked_patient_id IS NOT NULL)
        OR (role <> 'PATIENT' AND linked_patient_id IS NULL))
);

-- Usernames and emails are unique regardless of letter case.
CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (lower(username));
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));
-- One portal account per patient.
CREATE UNIQUE INDEX uq_app_user_linked_patient ON app_user (linked_patient_id)
    WHERE linked_patient_id IS NOT NULL;
