package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Staff account rules against a real PostgreSQL: unique names, last admin, self-protection, sessions. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StaffAccountServiceIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private StaffAccountService service;

    @Autowired
    private PasswordChangeService passwordChange;

    @Autowired
    private TokenService tokens;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Long> deactivatedForTest = List.of();

    @AfterEach
    void restoreAdmins() {
        for (Long id : deactivatedForTest) {
            jdbc.update("update app_user set status = 'ACTIVE' where user_id = ?", id);
        }
        deactivatedForTest = List.of();
    }

    private static String unique() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private AppUser saved(Role role) {
        String name = unique();
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name,
            name + "@example.org", role, null, false));
    }

    @Test
    void createGivesATemporaryPasswordThatMustBeChanged() {
        String name = unique();

        StaffCredential credential = service.create(name, "Nurse One", name + "@example.org", Role.NURSE);

        assertThat(credential.temporaryPassword()).hasSize(TemporaryPasswords.LENGTH);
        assertThat(credential.account().mustChangePassword()).isTrue();
        AppUser stored = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(encoder.matches(credential.temporaryPassword(), stored.getPasswordHash())).isTrue();
        assertThat(stored.getPasswordHash()).doesNotContain(credential.temporaryPassword());
    }

    @Test
    void createRefusesDuplicatesAndPatientRole() {
        String name = unique();
        service.create(name, "Nurse One", name + "@example.org", Role.NURSE);

        assertThatThrownBy(() -> service.create(name.toUpperCase(), "Other", unique() + "@example.org", Role.NURSE))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> service.create(unique(), "Other", name + "@example.org", Role.NURSE))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> service.create(unique(), "Other", unique() + "@example.org", Role.PATIENT))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aPatientAccountIsNotManagedHere() {
        long patientId = jdbc.queryForObject("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('T' || nextval('patient_mrn_seq'), 'Pat', 'Test', date '1990-01-01', 'OTHER') "
            + "returning patient_id", Long.class);
        AppUser patient = users.save(AppUser.create(unique(), "hash", "Pat", unique() + "@example.org",
            Role.PATIENT, patientId, false));

        assertThatThrownBy(() -> service.get(patient.getId()))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service.deactivate(1L, patient.getId()))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void roleChangeEndsAllSessionsOfThatUser() {
        AppUser doctor = saved(Role.DOCTOR);
        AppUser admin = saved(Role.ADMIN);
        IssuedSession session = tokens.startSession(new AuthenticatedUser(doctor.getId(), doctor.getUsername(),
            doctor.getFullName(), Role.DOCTOR, false));

        StaffAccountView updated = service.update(admin.getId(), doctor.getId(), "Nurse Now",
            doctor.getEmail(), Role.NURSE);

        assertThat(updated.role()).isEqualTo(Role.NURSE);
        assertThat(tokens.rotate(session.refreshToken())).isEmpty();
    }

    @Test
    void nobodyChangesOrDeactivatesThemselves() {
        AppUser admin = saved(Role.ADMIN);

        assertThatThrownBy(() -> service.deactivate(admin.getId(), admin.getId()))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
        assertThatThrownBy(() -> service.update(admin.getId(), admin.getId(), "X", admin.getEmail(), Role.DOCTOR))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void theLastActiveAdministratorStays() {
        AppUser a = saved(Role.ADMIN);
        AppUser b = saved(Role.ADMIN);
        deactivatedForTest = jdbc.queryForList(
            "select user_id from app_user where role = 'ADMIN' and status = 'ACTIVE' and user_id not in (?, ?)",
            Long.class, a.getId(), b.getId());
        deactivatedForTest.forEach(id ->
            jdbc.update("update app_user set status = 'INACTIVE' where user_id = ?", id));

        service.deactivate(b.getId(), a.getId()); // b may deactivate a: b stays

        assertThatThrownBy(() -> service.deactivate(a.getId(), b.getId()))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
        assertThatThrownBy(() -> service.update(a.getId(), b.getId(), "B", b.getEmail(), Role.DOCTOR))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void resetPasswordReplacesItForcesAChangeAndEndsSessions() {
        AppUser nurse = saved(Role.NURSE);
        IssuedSession session = tokens.startSession(new AuthenticatedUser(nurse.getId(), nurse.getUsername(),
            nurse.getFullName(), Role.NURSE, false));

        StaffCredential credential = service.resetPassword(nurse.getId());

        AppUser stored = users.findById(nurse.getId()).orElseThrow();
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isFalse();
        assertThat(encoder.matches(credential.temporaryPassword(), stored.getPasswordHash())).isTrue();
        assertThat(stored.isMustChangePassword()).isTrue();
        assertThat(tokens.rotate(session.refreshToken())).isEmpty();
    }

    @Test
    void changingThePasswordNeedsTheCurrentOneAndCountsFailures() {
        AppUser user = saved(Role.NURSE);
        IssuedSession session = tokens.startSession(new AuthenticatedUser(user.getId(), user.getUsername(),
            user.getFullName(), Role.NURSE, false));

        assertThat(passwordChange.change(user.getId(), "wrong-password-1", "Brand-New-Pass-77").outcome())
            .isEqualTo(PasswordChangeService.Outcome.WRONG_CURRENT_PASSWORD);
        assertThat(users.findById(user.getId()).orElseThrow().getFailedAttempts()).isEqualTo(1);

        assertThat(passwordChange.change(user.getId(), PASSWORD, "short1").outcome())
            .isEqualTo(PasswordChangeService.Outcome.NEW_PASSWORD_REJECTED);
        assertThat(passwordChange.change(user.getId(), PASSWORD, PASSWORD).outcome())
            .isEqualTo(PasswordChangeService.Outcome.NEW_PASSWORD_REJECTED);

        PasswordChangeService.Result done = passwordChange.change(user.getId(), PASSWORD, "Brand-New-Pass-77");
        assertThat(done.outcome()).isEqualTo(PasswordChangeService.Outcome.CHANGED);
        AppUser stored = users.findById(user.getId()).orElseThrow();
        assertThat(encoder.matches("Brand-New-Pass-77", stored.getPasswordHash())).isTrue();
        assertThat(stored.isMustChangePassword()).isFalse();
        assertThat(stored.getFailedAttempts()).isZero();
        assertThat(tokens.rotate(session.refreshToken())).isEmpty();
    }
}
