-- Refresh tokens for the sign-in session. Only a SHA-256 hash of the token is stored, never the
-- token itself, so a copy of this table cannot be used to sign in.
-- All tokens created from one sign-in share a family_id. Using a token a second time means it was
-- copied, so the whole family is revoked (see TokenService).
CREATE TABLE refresh_token (
    token_id          BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (user_id),
    token_hash        VARCHAR(64) NOT NULL,
    family_id         UUID        NOT NULL,
    issued_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    -- Absolute end of the whole family: rotation can never push a session past this moment.
    family_expires_at TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_hash_shape CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > issued_at AND expires_at <= family_expires_at)
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_family_expiry ON refresh_token (family_expires_at);
