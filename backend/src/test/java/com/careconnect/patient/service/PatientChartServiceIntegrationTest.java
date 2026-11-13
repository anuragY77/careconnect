package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientChartServiceIntegrationTest {

    @Autowired
    private PatientChartService chart;

    @Autowired
    private PatientService patients;

    @Autowired
    private JdbcTemplate jdbc;

    private long newPatient() {
        String last = "Ch" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return patients.register(new NewPatient("Asha", last, LocalDate.of(1990, 1, 1), Sex.FEMALE, null, null,
            null, null, null, null, null), false).patientId();
    }

    private long views(long patientId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = 'PATIENT_VIEWED' and patient_id = ?",
            Long.class, patientId);
    }

    @Test
    void openingAChartReturnsThePatientAndRecordsOneAuditEntryPerView() {
        long patient = newPatient();

        PatientView first = chart.open(new ChartActor("DOCTOR", null), patient);
        chart.open(new ChartActor("NURSE", null), patient);

        assertThat(first.patientId()).isEqualTo(patient);
        assertThat(first.mrn()).startsWith("CC-");
        assertThat(views(patient)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PATIENT_VIEWED' "
            + "and patient_id = ? and outcome = 'SUCCESS' and resource_type = 'PATIENT' and resource_id = ?", Long.class,
            patient, String.valueOf(patient))).isEqualTo(2L);
    }

    @Test
    void aCallerWithoutAccessGetsNothingAndLeavesNoViewEntry() {
        long patient = newPatient();

        for (ChartActor actor : new ChartActor[] {new ChartActor("PHARMACIST", null), new ChartActor("PATIENT", 999_999L),
            new ChartActor("PATIENT", null), new ChartActor(null, null), null}) {
            assertThat(catchThrowable(() -> chart.open(actor, patient))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        }
        assertThat(views(patient)).isZero();
    }

    @Test
    void anUnknownPatientIsNotFoundAndNotAudited() {
        long before = jdbc.queryForObject("select count(*) from audit_event where action = 'PATIENT_VIEWED'", Long.class);

        assertThat(catchThrowable(() -> chart.open(new ChartActor("DOCTOR", null), 999_999_999L)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PATIENT_VIEWED'", Long.class))
            .isEqualTo(before);
    }
}
