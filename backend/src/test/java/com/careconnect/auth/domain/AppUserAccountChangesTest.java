package com.careconnect.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AppUserAccountChangesTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private static AppUser staff(Role role) {
        return AppUser.create("dr.rao", "hash", "Dr Rao", "rao@example.org", role, null, false);
    }

    @Test
    void staffMoveBetweenStaffRolesOnly() {
        AppUser user = staff(Role.DOCTOR);
        user.changeStaffRole(Role.NURSE);
        assertThat(user.getRole()).isEqualTo(Role.NURSE);

        assertThatThrownBy(() -> user.changeStaffRole(Role.PATIENT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeStaffRole(null)).isInstanceOf(IllegalArgumentException.class);

        AppUser patient = AppUser.create("pat", "hash", "Pat", "pat@example.org", Role.PATIENT, 7L, false);
        assertThatThrownBy(() -> patient.changeStaffRole(Role.DOCTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacingThePasswordClearsTheLockoutAndSetsTheChangeFlag() {
        AppUser user = staff(Role.DOCTOR);
        user.recordFailedLogin(NOW, 1, Duration.ofMinutes(15));
        assertThat(user.isLockedAt(NOW)).isTrue();

        user.replacePassword("new-hash", true);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    void deactivateAndActivate() {
        AppUser user = staff(Role.DOCTOR);
        user.recordFailedLogin(NOW, 1, Duration.ofMinutes(15));

        user.deactivate();
        assertThat(user.isActive()).isFalse();

        user.activate();
        assertThat(user.isActive()).isTrue();
        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void updateProfileTrimsAndRejectsBlank() {
        AppUser user = staff(Role.DOCTOR);
        user.updateProfile("  Dr Rao Jr ", " new@example.org ");
        assertThat(user.getFullName()).isEqualTo("Dr Rao Jr");
        assertThat(user.getEmail()).isEqualTo("new@example.org");
        assertThatThrownBy(() -> user.updateProfile(" ", "x@example.org")).isInstanceOf(IllegalArgumentException.class);
    }
}
