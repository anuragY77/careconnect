package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.PatientStatus;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Registration rules against a real PostgreSQL (TC-10). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientServiceIntegrationTest {

    @Autowired
    private PatientService service;

    @Autowired
    private JdbcTemplate jdbc;

    private static NewPatient patient(String first, String last, LocalDate dob) {
        return new NewPatient(first, last, dob, Sex.FEMALE, null, null, null, null, null, null, null);
    }

    private long patientCount() {
        return jdbc.queryForObject("select count(*) from patient", Long.class);
    }

    @Test
    void registersWithAGeneratedMrnAndCleansTheInput() {
        NewPatient input = new NewPatient("  Asha ", "Rao   Singh", LocalDate.of(1990, 5, 17), Sex.FEMALE,
            " 98765 43210 ", "ASHA@Example.ORG", "  ", "  Ravi Rao ", "", " Star Health ", " P-123 ");

        PatientView view = service.register(input, false);

        assertThat(view.mrn()).matches("CC-\\d{6,}");
        assertThat(view.firstName()).isEqualTo("Asha");
        assertThat(view.lastName()).isEqualTo("Rao Singh");
        assertThat(view.phone()).isEqualTo("98765 43210");
        assertThat(view.email()).isEqualTo("asha@example.org");
        assertThat(view.address()).isNull();
        assertThat(view.emergencyContactName()).isEqualTo("Ravi Rao");
        assertThat(view.emergencyContactPhone()).isNull();
        assertThat(view.insuranceProvider()).isEqualTo("Star Health");
        assertThat(view.insurancePolicyNumber()).isEqualTo("P-123");
        assertThat(view.status()).isEqualTo(PatientStatus.ACTIVE);
        Map<String, Object> row = jdbc.queryForMap("select * from patient where patient_id = ?", view.patientId());
        assertThat(row).containsEntry("mrn", view.mrn()).containsEntry("sex", "FEMALE");
    }

    @Test
    void everyPatientGetsADifferentMrn() {
        NewPatient input = patient("A", "One", LocalDate.of(2000, 1, 1));
        PatientView a = service.register(input, false);

        assertThatThrownBy(() -> service.register(input, false))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POSSIBLE_DUPLICATE));

        PatientView b = service.register(input, true);

        assertThat(a.mrn()).isNotEqualTo(b.mrn());
        assertThat(Long.parseLong(b.mrn().substring(3))).isGreaterThan(Long.parseLong(a.mrn().substring(3)));
    }

    @Test
    void theDateOfBirthMustBePlausible() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        long before = patientCount();

        assertThatThrownBy(() -> service.register(patient("A", "B", todayUtc.plusDays(2)), false))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.register(patient("A", "B", LocalDate.of(1899, 12, 31)), false))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(patientCount()).isEqualTo(before);

        // Born just after midnight in India: still tomorrow in UTC (V6).
        assertThat(service.register(patient("New", "Born", todayUtc.plusDays(1)), false).patientId()).isPositive();
        assertThat(service.register(patient("Same", "Day", todayUtc), false).patientId()).isPositive();
    }

    @Test
    void namesAreRequired() {
        assertThatThrownBy(() -> service.register(patient("   ", "B", LocalDate.of(2000, 1, 1)), false))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.register(patient("A", null, LocalDate.of(2000, 1, 1)), false))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void registrationIsAudited() {
        PatientView view = service.register(patient("Audit", "Me", LocalDate.of(1985, 3, 3)), false);

        Map<String, Object> event = jdbc.queryForMap("select action, outcome, patient_id, resource_type, resource_id, "
            + "details ->> 'mrn' as mrn, details::text as details from audit_event "
            + "where action = 'PATIENT_REGISTERED' and patient_id = ?", view.patientId());
        assertThat(event).containsEntry("outcome", "SUCCESS").containsEntry("resource_type", "PATIENT")
            .containsEntry("resource_id", String.valueOf(view.patientId())).containsEntry("mrn", view.mrn());
        // Identifiers only: the audit trail never carries names.
        assertThat((String) event.get("details")).doesNotContain("Audit").doesNotContain("Me\"");
    }
}
