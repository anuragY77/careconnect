-- Append-only audit trail (FR-37, NFR-08). Rows are only ever inserted and read.
-- user_id has no foreign key on purpose: a failed login for an unknown user must be recorded too,
-- and an audit row must survive any later change to the user table. username and role are copied
-- into the row for the same reason.
-- patient_id has no foreign key because the patient table arrives in a later migration; the
-- constraint is added there.
CREATE TABLE audit_event (
    event_id      BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    user_id       BIGINT,
    username      VARCHAR(50),
    role          VARCHAR(30),
    action        VARCHAR(40)  NOT NULL,
    outcome       VARCHAR(10)  NOT NULL,
    patient_id    BIGINT,
    resource_type VARCHAR(40),
    resource_id   VARCHAR(64),
    client_ip     VARCHAR(45),
    -- Identifiers and before/after values only; never free clinical text (ARCHITECTURE 3.6).
    details       JSONB,

    CONSTRAINT ck_audit_event_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);

CREATE INDEX ix_audit_event_time ON audit_event (occurred_at);
CREATE INDEX ix_audit_event_patient_time ON audit_event (patient_id, occurred_at);
CREATE INDEX ix_audit_event_user_time ON audit_event (user_id, occurred_at);

CREATE FUNCTION audit_event_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_reject_change();

CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_reject_change();
