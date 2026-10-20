package com.careconnect.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real PostgreSQL container. Every test rolls back, so tests
 * do not affect each other. Constraint tests check the constraint name, so a
 * failure for a different reason cannot pass by accident.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AppUserRepositoryIntegrationTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EntityManager entityManager;

    private AppUser doctor(String username, String email) {
        return AppUser.create(username, "hash", "Dr " + username, email, Role.DOCTOR, null, false);
    }

    @Test
    void savesAndLoadsAUserWithDefaults() {
        AppUser saved = users.saveAndFlush(doctor("dr.smith", "smith@example.org"));
        entityManager.clear();

        AppUser loaded = users.findByUsernameIgnoreCase("DR.SMITH").orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getFailedAttempts()).isZero();
        assertThat(loaded.getLockedUntil()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void existenceChecksIgnoreLetterCase() {
        users.saveAndFlush(doctor("dr.smith", "smith@example.org"));

        assertThat(users.existsByUsernameIgnoreCase("Dr.Smith")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("SMITH@example.org")).isTrue();
        assertThat(users.existsByUsernameIgnoreCase("someone.else")).isFalse();
    }

    @Test
    void usernameIsUniqueIgnoringLetterCase() {
        users.saveAndFlush(doctor("nurse.joy", "joy@example.org"));

        assertThatThrownBy(() -> users.saveAndFlush(doctor("NURSE.JOY", "other@example.org")))
            .hasStackTraceContaining("uq_app_user_username_lower");
    }

    @Test
    void emailIsUniqueIgnoringLetterCase() {
        users.saveAndFlush(doctor("first", "same@example.org"));

        assertThatThrownBy(() -> users.saveAndFlush(doctor("second", "SAME@example.org")))
            .hasStackTraceContaining("uq_app_user_email_lower");
    }

    @Test
    void onePatientCanHaveOnlyOnePortalAccount() {
        users.saveAndFlush(AppUser.create("pat.one", "hash", "Pat One", "p1@example.org", Role.PATIENT, 42L, false));

        assertThatThrownBy(() -> users.saveAndFlush(
                AppUser.create("pat.two", "hash", "Pat Two", "p2@example.org", Role.PATIENT, 42L, false)))
            .hasStackTraceContaining("uq_app_user_linked_patient");
    }

    @Test
    void databaseRejectsAPatientWithoutALink() {
        assertThatThrownBy(() -> insertRaw("pat", "PATIENT", "NULL"))
            .hasStackTraceContaining("ck_app_user_patient_link");
    }

    @Test
    void databaseRejectsAStaffUserWithALink() {
        assertThatThrownBy(() -> insertRaw("doc", "DOCTOR", "5"))
            .hasStackTraceContaining("ck_app_user_patient_link");
    }

    @Test
    void databaseRejectsAnUnknownRole() {
        assertThatThrownBy(() -> insertRaw("hacker", "HACKER", "NULL"))
            .hasStackTraceContaining("ck_app_user_role");
    }

    /** Bypasses the entity on purpose, to prove the database enforces the rules by itself. */
    private void insertRaw(String username, String role, String linkedPatientId) {
        entityManager.createNativeQuery(
            "insert into app_user (username, password_hash, full_name, email, role, linked_patient_id) "
                + "values ('" + username + "', 'hash', 'Raw User', '" + username + "@example.org', '"
                + role + "', " + linkedPatientId + ")").executeUpdate();
    }
}
