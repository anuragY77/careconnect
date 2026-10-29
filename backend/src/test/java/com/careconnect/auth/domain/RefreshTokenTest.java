package com.careconnect.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final String HASH = "a".repeat(64);

    private RefreshToken token(Instant expires, Instant familyEnd) {
        return RefreshToken.issue(1L, HASH, UUID.randomUUID(), T0, expires, familyEnd);
    }

    @Test
    void newTokenIsUnusedAndNotRevoked() {
        RefreshToken token = token(T0.plusSeconds(100), T0.plusSeconds(200));

        assertThat(token.isUsed()).isFalse();
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void expiresExactlyAtItsOwnTime() {
        RefreshToken token = token(T0.plusSeconds(100), T0.plusSeconds(200));

        assertThat(token.isExpiredAt(T0.plusSeconds(99))).isFalse();
        assertThat(token.isExpiredAt(T0.plusSeconds(100))).isTrue();
    }

    @Test
    void expiresWhenTheFamilyEnds() {
        RefreshToken token = token(T0.plusSeconds(200), T0.plusSeconds(200));

        assertThat(token.isExpiredAt(T0.plusSeconds(199))).isFalse();
        assertThat(token.isExpiredAt(T0.plusSeconds(200))).isTrue();
    }

    @Test
    void firstUseTimeWins() {
        RefreshToken token = token(T0.plusSeconds(100), T0.plusSeconds(200));

        token.markUsed(T0.plusSeconds(10));
        token.markUsed(T0.plusSeconds(50));

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void rejectsInvalidInput() {
        UUID family = UUID.randomUUID();
        assertThatThrownBy(() -> RefreshToken.issue(null, HASH, family, T0, T0.plusSeconds(1), T0.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, " ", family, T0, T0.plusSeconds(1), T0.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, HASH, null, T0, T0.plusSeconds(1), T0.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, HASH, family, T0, T0, T0.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, HASH, family, T0, T0.plusSeconds(3), T0.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
