package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.AllergySeverity;
import com.careconnect.patient.domain.AllergyStatus;
import com.careconnect.patient.domain.ProblemStatus;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChartSummaryServiceIntegrationTest {

    private static final ChartActor DOCTOR = new ChartActor("DOCTOR", null);

    @Autowired
    private PatientChartService chart;

    @Autowired
    private PatientService patients;

    @Autowired
    private AllergyService allergies;

    @Autowired
    private ProblemService problems;

    @Autowired
    private JdbcTemplate jdbc;

    private long newPatient() {
        String last = "Sm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return patients.register(new NewPatient("Asha", last, LocalDate.of(1990, 1, 1), Sex.FEMALE, null, null,
            null, null, null, null, null), false).patientId();
    }

    private long newDoctor() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("insert into app_user (username, password_hash, full_name, email, role) "
            + "values (?, 'hash', 'Dr X', ?, 'DOCTOR') returning user_id", Long.class, name, name + "@example.org");
    }

    private long charts(long patientId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = 'CHART_VIEWED' and patient_id = ?",
            Long.class, patientId);
    }

    @Test
    void aNewPatientHasAnEmptyChart() {
        long patient = newPatient();

        ChartSummary summary = chart.summary(DOCTOR, patient);

        assertThat(summary.patient().patientId()).isEqualTo(patient);
        assertThat(summary.allergies()).isEmpty();
        assertThat(summary.problems()).isEmpty();
    }

    @Test
    void allergiesListActiveFirstThenBySeverityAndKeepEveryStatusVisible() {
        long patient = newPatient();
        long doctor = newDoctor();
        allergies.add(doctor, patient, "Dust", null, AllergySeverity.MILD);
        allergies.add(doctor, patient, "Penicillin", null, AllergySeverity.SEVERE);
        AllergyView pollen = allergies.add(doctor, patient, "Pollen", null, AllergySeverity.MODERATE);
        AllergyView wrong = allergies.add(doctor, patient, "Wrong", null, AllergySeverity.SEVERE);
        allergies.changeStatus(pollen.allergyId(), pollen.version(), AllergyStatus.INACTIVE, "Outgrown");
        allergies.changeStatus(wrong.allergyId(), wrong.version(), AllergyStatus.ENTERED_IN_ERROR, "Wrong patient");

        ChartSummary summary = chart.summary(DOCTOR, patient);

        assertThat(summary.allergies()).extracting(AllergyView::substance)
            .containsExactly("Penicillin", "Dust", "Pollen", "Wrong");
        assertThat(summary.allergies()).extracting(AllergyView::status).containsExactly(AllergyStatus.ACTIVE,
            AllergyStatus.ACTIVE, AllergyStatus.INACTIVE, AllergyStatus.ENTERED_IN_ERROR);
    }

    @Test
    void problemsListActiveFirstAndOnlyThisPatientsRowsAppear() {
        long patient = newPatient();
        long other = newPatient();
        long doctor = newDoctor();
        ProblemView hypertension = problems.add(doctor, patient, "I10", null);
        problems.add(doctor, patient, "E11.9", null);
        problems.add(doctor, other, "J45.909", null);
        allergies.add(doctor, other, "Latex", null, AllergySeverity.MILD);
        problems.changeStatus(hypertension.problemId(), hypertension.version(), ProblemStatus.RESOLVED, null);

        ChartSummary summary = chart.summary(DOCTOR, patient);

        assertThat(summary.problems()).extracting(ProblemView::icd10Code).containsExactly("E11.9", "I10");
        assertThat(summary.problems()).extracting(ProblemView::status)
            .containsExactly(ProblemStatus.ACTIVE, ProblemStatus.RESOLVED);
        assertThat(summary.allergies()).isEmpty();
    }

    @Test
    void everySummaryViewIsAuditedWithoutClinicalText() {
        long patient = newPatient();
        allergies.add(newDoctor(), patient, "Zzz-Secret-Substance", null, AllergySeverity.MILD);

        chart.summary(DOCTOR, patient);
        chart.summary(new ChartActor("NURSE", null), patient);

        assertThat(charts(patient)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'CHART_VIEWED' "
            + "and patient_id = ? and details::text like '%Secret%'", Long.class, patient)).isZero();
    }

    @Test
    void callersWithoutFullAccessGetNothingAndLeaveNoEntry() {
        long patient = newPatient();

        for (ChartActor actor : new ChartActor[] {new ChartActor("RECEPTIONIST", null), new ChartActor("PHARMACIST", null),
            new ChartActor("PATIENT", 999_999L), new ChartActor(null, null), null}) {
            assertThat(catchThrowable(() -> chart.summary(actor, patient))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        }
        assertThat(charts(patient)).isZero();
    }

    @Test
    void anUnknownPatientIsNotFound() {
        assertThat(catchThrowable(() -> chart.summary(DOCTOR, 999_999_999L))).isInstanceOfSatisfying(ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
