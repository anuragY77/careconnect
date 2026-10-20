package com.careconnect.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void usesBcryptWithCost12() {
        assertThat(encoder.encode("Some-Password-123")).startsWith("$2a$12$");
    }

    @Test
    void matchesTheRightPasswordOnly() {
        String hash = encoder.encode("Some-Password-123");

        assertThat(encoder.matches("Some-Password-123", hash)).isTrue();
        assertThat(encoder.matches("Some-Password-124", hash)).isFalse();
    }

    @Test
    void sameInputGivesDifferentHashesBecauseOfTheSalt() {
        assertThat(encoder.encode("Some-Password-123")).isNotEqualTo(encoder.encode("Some-Password-123"));
    }

    @Test
    void hashFitsTheDatabaseColumn() {
        assertThat(encoder.encode("Some-Password-123").length()).isLessThanOrEqualTo(100);
    }
}
