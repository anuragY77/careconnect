package com.careconnect.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppUserTest {

    @Test
    void createsAnActiveStaffUserWithCleanValues() {
        AppUser user = AppUser.create("  dr.smith ", "hash", " Dr Smith ", " smith@example.org ",
            Role.DOCTOR, null, true);

        assertThat(user.getUsername()).isEqualTo("dr.smith");
        assertThat(user.getFullName()).isEqualTo("Dr Smith");
        assertThat(user.getEmail()).isEqualTo("smith@example.org");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getLinkedPatientId()).isNull();
    }

    @Test
    void patientUserMustBeLinkedToAPatient() {
        assertThatThrownBy(() -> AppUser.create("pat", "hash", "Pat", "pat@example.org", Role.PATIENT, null, false))
            .isInstanceOf(IllegalArgumentException.class);
        AppUser patient = AppUser.create("pat", "hash", "Pat", "pat@example.org", Role.PATIENT, 7L, false);
        assertThat(patient.getLinkedPatientId()).isEqualTo(7L);
    }

    @Test
    void staffUserCannotBeLinkedToAPatient() {
        assertThatThrownBy(() -> AppUser.create("nurse", "hash", "Nurse", "nurse@example.org", Role.NURSE, 7L, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredValuesAreChecked() {
        assertThatThrownBy(() -> AppUser.create(" ", "hash", "Name", "a@example.org", Role.ADMIN, null, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppUser.create("admin", "", "Name", "a@example.org", Role.ADMIN, null, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppUser.create("admin", "hash", "Name", "a@example.org", null, null, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPatientIsNotStaff() {
        for (Role role : Role.values()) {
            assertThat(role.isStaff()).isEqualTo(role != Role.PATIENT);
        }
    }
}
