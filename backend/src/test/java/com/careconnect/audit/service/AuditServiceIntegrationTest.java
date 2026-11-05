package com.careconnect.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.careconnect.auth.service.LoginService;
import com.careconnect.auth.service.PasswordChangeService;
import com.careconnect.auth.service.StaffAccountService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;

/** What gets audited by the services, against a real PostgreSQL (TC-74 at service level). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditServiceIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private AuditService audit;

    @Autowired
    private LoginService login;

    @Autowired
    private PasswordChangeService passwordChange;

    @Autowired
    private StaffAccountService staff;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private static String unique() {
        return "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private AppUser saved(Role role) {
        String name = unique();
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name,
            name + "@example.org", role, null, false));
    }

    private List<Map<String, Object>> rowsFor(String username) {
        return jdbc.queryForList("select * from audit_event where username = ? order by event_id", username);
    }

    @Test
    void anEventCannotBeWrittenOutsideATransaction() {
        assertThatThrownBy(() -> audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.SUCCESS)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void successfulLoginIsAudited() {
        AppUser user = saved(Role.DOCTOR);

        assertThat(login.login(user.getUsername(), PASSWORD)).isPresent();

        List<Map<String, Object>> rows = rowsFor(user.getUsername());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "LOGIN").containsEntry("outcome", "SUCCESS")
            .containsEntry("user_id", user.getId()).containsEntry("role", "DOCTOR");
    }

    @Test
    void everyKindOfLoginFailureIsAuditedAndSurvives() {
        AppUser user = saved(Role.NURSE);
        String unknown = unique();

        assertThat(login.login(user.getUsername(), "wrong-password-1")).isEmpty();
        assertThat(login.login(unknown, PASSWORD)).isEmpty();

        List<Map<String, Object>> known = rowsFor(user.getUsername());
        assertThat(known).hasSize(1);
        assertThat(known.get(0)).containsEntry("action", "LOGIN").containsEntry("outcome", "FAILURE")
            .containsEntry("user_id", user.getId());
        assertThat(jdbc.queryForObject("select details->>'reason' from audit_event where event_id = ?",
            String.class, known.get(0).get("event_id"))).isEqualTo("BAD_PASSWORD");

        List<Map<String, Object>> missing = rowsFor(unknown);
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0)).containsEntry("outcome", "FAILURE").containsEntry("user_id", null);
        assertThat(jdbc.queryForObject("select details->>'reason' from audit_event where event_id = ?",
            String.class, missing.get(0).get("event_id"))).isEqualTo("UNKNOWN_USER");
    }

    @Test
    void aVeryLongUnknownUsernameStillGetsAuditedAndTheLoginStillFailsCleanly() {
        String longName = "x".repeat(300);

        assertThat(login.login(longName, PASSWORD)).isEmpty();

        assertThat(rowsFor("x".repeat(50))).isNotEmpty();
    }

    @Test
    void passwordChangesAreAuditedWithoutAnyPassword() {
        AppUser user = saved(Role.NURSE);

        passwordChange.change(user.getId(), "wrong-password-1", "Brand-New-Pass-77");
        passwordChange.change(user.getId(), PASSWORD, "Brand-New-Pass-77");

        List<Map<String, Object>> rows = rowsFor(user.getUsername());
        assertThat(rows).extracting(r -> r.get("action") + ":" + r.get("outcome"))
            .containsExactly("PASSWORD_CHANGED:FAILURE", "PASSWORD_CHANGED:SUCCESS");
        assertThat(rows.toString()).doesNotContain("Brand-New-Pass-77").doesNotContain(PASSWORD);
    }

    @Test
    void staffAccountActionsAreAuditedWithoutThePassword() {
        String name = unique();
        var credential = staff.create(name, "Nurse One", name + "@example.org", Role.NURSE);
        long id = credential.account().userId();
        staff.update(1L, id, "Nurse Two", name + "@example.org", Role.PHARMACIST);
        staff.unlock(id);
        var reset = staff.resetPassword(id);
        staff.deactivate(1L, id);
        staff.activate(id);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "select * from audit_event where resource_type = 'STAFF_ACCOUNT' and resource_id = ? order by event_id",
            String.valueOf(id));
        assertThat(rows).extracting(r -> (String) r.get("action")).containsExactly("STAFF_CREATED",
            "STAFF_UPDATED", "STAFF_UNLOCKED", "STAFF_PASSWORD_RESET", "STAFF_DEACTIVATED", "STAFF_ACTIVATED");
        assertThat(jdbc.queryForObject(
            "select details->>'roleBefore' || '>' || (details->>'roleAfter') from audit_event "
                + "where resource_id = ? and action = 'STAFF_UPDATED'", String.class, String.valueOf(id)))
            .isEqualTo("NURSE>PHARMACIST");
        assertThat(rows.toString()).doesNotContain(credential.temporaryPassword())
            .doesNotContain(reset.temporaryPassword());
    }
}
