package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Demographics update against a real PostgreSQL (TC-14). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientUpdateServiceIntegrationTest {

    private static final LocalDate DOB = LocalDate.of(1992, 4, 9);

    @Autowired
    private PatientService service;

    @Autowired
    private JdbcTemplate jdbc;

    private static String unique() {
        return "Up" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private PatientView registered(String last) {
        return service.register(new NewPatient("Asha", last, DOB, Sex.FEMALE, "98765 43210", "asha@example.org",
            "12 Park Road", "Ravi Rao", "91234 56789", "Star Health", "P-111"), true);
    }

    /** The same demographics as {@link #registered}, with the given changes. */
    private static NewPatient edited(String last, String phone, String address) {
        return new NewPatient("Asha", last, DOB, Sex.FEMALE, phone, "asha@example.org", address, "Ravi Rao",
            "91234 56789", "Star Health", "P-111");
    }

    private List<Map<String, Object>> updateEvents(long patientId) {
        return jdbc.queryForList("select details::text as details, details ->> 'changed' as changed, "
            + "details ->> 'phone.before' as phone_before, details ->> 'phone.after' as phone_after, "
            + "details ->> 'lastName.before' as last_before, details ->> 'lastName.after' as last_after, "
            + "details ->> 'mrn' as mrn, outcome from audit_event "
            + "where action = 'PATIENT_UPDATED' and patient_id = ? order by event_id", patientId);
    }

    @Test
    void updatesTheDemographicsCleansTheInputAndKeepsTheMrn() {
        String last = unique();
        PatientView before = registered(last);

        PatientView after = service.update(before.patientId(), before.version(),
            new NewPatient("  Asha ", " " + last + " ", DOB, Sex.FEMALE, " 99999 88888 ", "ASHA@Example.ORG", "  ",
                "Ravi Rao", "", "Star Health", "P-111"));

        assertThat(after.mrn()).isEqualTo(before.mrn());
        assertThat(after.patientId()).isEqualTo(before.patientId());
        assertThat(after.phone()).isEqualTo("99999 88888");
        assertThat(after.address()).isNull();
        assertThat(after.emergencyContactPhone()).isNull();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        Map<String, Object> row = jdbc.queryForMap("select phone, address, version, mrn from patient where patient_id = ?",
            before.patientId());
        assertThat(row).containsEntry("phone", "99999 88888").containsEntry("mrn", before.mrn());
        assertThat(row.get("address")).isNull();
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(after.version());
    }

    @Test
    void theAuditEntryHasBeforeAndAfterOfTheIdentifyingFieldsAndOnlyTheNameOfFreeTextFields() {
        String last = unique();
        String newLast = unique();
        PatientView before = registered(last);

        service.update(before.patientId(), before.version(), edited(newLast, "99999 88888", "7 New Street, Pune"));

        List<Map<String, Object>> events = updateEvents(before.patientId());
        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);
        assertThat(event).containsEntry("outcome", "SUCCESS").containsEntry("mrn", before.mrn())
            .containsEntry("changed", "lastName,phone,address")
            .containsEntry("phone_before", "98765 43210").containsEntry("phone_after", "99999 88888")
            .containsEntry("last_before", last).containsEntry("last_after", newLast);
        // Free text is never copied into the audit trail.
        assertThat((String) event.get("details")).doesNotContain("Park Road").doesNotContain("New Street")
            .doesNotContain("Ravi").doesNotContain("P-111");
    }

    @Test
    void anUpdateThatChangesNothingSavesAndRecordsNothing() {
        String last = unique();
        PatientView before = registered(last);

        PatientView same = service.update(before.patientId(), before.version(), edited(last, "98765 43210", "12 Park Road"));

        assertThat(same.version()).isEqualTo(before.version());
        assertThat(updateEvents(before.patientId())).isEmpty();
    }

    @Test
    void aStaleVersionIsRefusedAndNothingIsSaved() {
        String last = unique();
        PatientView first = registered(last);
        PatientView second = service.update(first.patientId(), first.version(), edited(last, "99999 88888", "12 Park Road"));

        // A second screen still shows the old version.
        assertThatThrownBy(() -> service.update(first.patientId(), first.version(), edited(last, "11111 22222", "12 Park Road")))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));

        assertThat(jdbc.queryForObject("select phone from patient where patient_id = ?", String.class, first.patientId()))
            .isEqualTo("99999 88888");
        assertThat(updateEvents(first.patientId())).hasSize(1);
        // The current version works, and every change counts one up.
        PatientView third = service.update(second.patientId(), second.version(), edited(last, "11111 22222", "12 Park Road"));
        assertThat(third.version()).isEqualTo(second.version() + 1);
    }

    @Test
    void anUnknownPatientIsNotFound() {
        assertThatThrownBy(() -> service.update(999_999_999L, 0, edited(unique(), null, null)))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void theSameRulesAsRegistrationApplyAndNothingChangesOnRefusal() {
        String last = unique();
        PatientView before = registered(last);
        LocalDate future = LocalDate.now().plusYears(1);

        assertThatThrownBy(() -> service.update(before.patientId(), before.version(),
            new NewPatient("Asha", last, future, Sex.FEMALE, null, null, null, null, null, null, null)))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.update(before.patientId(), before.version(),
            new NewPatient("  ", last, DOB, Sex.FEMALE, null, null, null, null, null, null, null)))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThat(jdbc.queryForObject("select version from patient where patient_id = ?", Long.class, before.patientId()))
            .isEqualTo(before.version());
        assertThat(updateEvents(before.patientId())).isEmpty();
    }
}
