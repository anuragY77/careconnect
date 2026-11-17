CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE provider_availability (
    availability_id BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT      NOT NULL REFERENCES app_user (user_id),
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    slot_minutes    INTEGER     NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      BIGINT      NOT NULL REFERENCES app_user (user_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_availability_status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_availability_order CHECK (end_time > start_time),
    CONSTRAINT ck_availability_slot CHECK (slot_minutes BETWEEN 5 AND 240),
    CONSTRAINT ck_availability_length CHECK (end_time - start_time <= INTERVAL '24 hours'),
    CONSTRAINT ck_availability_whole_slots CHECK (
        MOD(EXTRACT(EPOCH FROM (end_time - start_time))::BIGINT, slot_minutes * 60) = 0),
    CONSTRAINT ex_availability_no_overlap EXCLUDE USING gist (
        provider_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&) WHERE (status = 'ACTIVE')
);

CREATE INDEX ix_availability_provider_start ON provider_availability (provider_id, start_time);
