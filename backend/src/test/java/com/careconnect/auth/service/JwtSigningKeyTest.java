package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSigningKeyTest {

    private static final String GOOD = "0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEF";

    @Test
    void acceptsALongEnoughSecret() {
        assertThat(JwtSigningKey.from(GOOD, false).getEncoded()).hasSize(GOOD.length());
    }

    @Test
    void rejectsMissingOrBlankSecret() {
        assertThatThrownBy(() -> JwtSigningKey.from(null, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSigningKey.from("   ", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> JwtSigningKey.from("a".repeat(31), true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32");
        assertThat(JwtSigningKey.from("a".repeat(32), true)).isNotNull();
    }

    @Test
    void exampleSecretIsAllowedOnlyInTheLocalProfile() {
        String example = "dev-only-secret-change-me-dev-only-secret-change-me-0123456789";

        assertThat(JwtSigningKey.from(example, true)).isNotNull();
        assertThatThrownBy(() -> JwtSigningKey.from(example, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("example");
    }
}
