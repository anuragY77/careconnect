package com.careconnect.auth.service;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Builds the HS256 signing key from JWT_SECRET and refuses weak or example secrets, so the
 * application fails at startup instead of running with a key anyone can guess.
 */
public final class JwtSigningKey {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_BYTES = 32;

    /** Start of the example value in .env.example. Allowed only in the local profile. */
    static final String EXAMPLE_PREFIX = "dev-only-secret-change-me";

    private JwtSigningKey() {
    }

    public static SecretKey from(String secret, boolean localProfile) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException("JWT_SECRET is too short: it must be at least " + MIN_BYTES
                + " bytes. Generate one with: openssl rand -base64 48");
        }
        if (!localProfile && secret.startsWith(EXAMPLE_PREFIX)) {
            throw new IllegalStateException("JWT_SECRET is still the example value from .env.example. "
                + "Generate a real one with: openssl rand -base64 48");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
