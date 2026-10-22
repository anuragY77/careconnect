package com.careconnect.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppUserLoginRulesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.create("nurse.joy", "hash", "Nurse Joy", "joy@example.org", Role.NURSE, null, false);
    }

    private void fail(Instant at) {
        user.recordFailedLogin(at, 5, LOCK);
    }

    @Test
    void fourFailuresDoNotLock() {
        for (int i = 0; i < 4; i++) {
            fail(T0);
        }

        assertThat(user.getFailedAttempts()).isEqualTo(4);
        assertThat(user.isLockedAt(T0)).isFalse();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void fifthFailureLocksForTheConfiguredTime() {
        for (int i = 0; i < 5; i++) {
            fail(T0);
        }

        assertThat(user.getLockedUntil()).isEqualTo(T0.plus(LOCK));
        assertThat(user.isLockedAt(T0.plusSeconds(1))).isTrue();
    }

    @Test
    void lockEndsExactlyAtLockedUntil() {
        for (int i = 0; i < 5; i++) {
            fail(T0);
        }

        assertThat(user.isLockedAt(T0.plus(LOCK).minusMillis(1))).isTrue();
        assertThat(user.isLockedAt(T0.plus(LOCK))).isFalse();
    }

    @Test
    void failuresWhileLockedChangeNothing() {
        for (int i = 0; i < 5; i++) {
            fail(T0);
        }
        Instant lockEnd = user.getLockedUntil();

        fail(T0.plusSeconds(60));
        fail(T0.plusSeconds(120));

        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(lockEnd);
    }

    @Test
    void countStartsAgainAfterALockHasEnded() {
        for (int i = 0; i < 5; i++) {
            fail(T0);
        }

        fail(T0.plus(LOCK));

        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLockedAt(T0.plus(LOCK))).isFalse();
    }

    @Test
    void successfulLoginClearsFailuresAndRemembersTheTime() {
        fail(T0);
        fail(T0);

        user.recordSuccessfulLogin(T0.plusSeconds(10));

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void successfulLoginAfterAnEndedLockClearsTheOldLock() {
        for (int i = 0; i < 5; i++) {
            fail(T0);
        }

        user.recordSuccessfulLogin(T0.plus(LOCK));

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> user.recordFailedLogin(T0, 0, LOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.recordFailedLogin(T0, 5, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.recordFailedLogin(T0, 5, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newUserIsActive() {
        assertThat(user.isActive()).isTrue();
    }
}
