package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.AllergySeverity;
import com.careconnect.patient.domain.AllergyStatus;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Allergy rules against a real PostgreSQL (TC-15, TC-16). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AllergyServiceIntegrationTest {

    @Autowired
    private AllergyService service;

    @Autowired
    private PatientService patients;

    @Autowired
    private JdbcTemplate jdbc;

    private static String unique() {
        return "Al" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private long newPatient() {
        return patients.register(new NewPatient("Asha", unique(), LocalDate.of(1990, 1, 1), Sex.FEMALE, null, null,
            null, null, null, null, null), false).patientId();
    }

    private long newDoctor() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("insert into app_user (username, password_hash, full_name, email, role) "
            + "values (?, 'hash', 'Dr X', ?, 'DOCTOR') returning user_id", Long.class, name, name + "@example.org");
    }

    private static void assertCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void addsAnActiveAllergyRecordedByTheSignedInUser() {
        long patient = newPatient();
        long doctor = newDoctor();

        AllergyView view = service.add(doctor, patient, "  Penicillin   G ", " ", AllergySeverity.SEVERE);

        assertThat(view.substance()).isEqualTo("Penicillin G");
        assertThat(view.reaction()).isNull();
        assertThat(view.status()).isEqualTo(AllergyStatus.ACTIVE);
        assertThat(view.recordedBy()).isEqualTo(doctor);
        assertThat(view.patientId()).isEqualTo(patient);
        assertThat(view.version()).isZero();
        assertThat(jdbc.queryForObject("select status from allergy where allergy_id = ?", String.class, view.allergyId()))
            .isEqualTo("ACTIVE");
    }

    @Test
    void addingIsAuditedWithoutTheClinicalText() {
        long patient = newPatient();

        AllergyView view = service.add(newDoctor(), patient, "Zzz-Secret-Substance", "Zzz-Secret-Reaction", AllergySeverity.MILD);

        Map<String, Object> event = jdbc.queryForMap("select outcome, resource_type, resource_id, "
            + "details ->> 'severity' as severity, details::text as details from audit_event "
            + "where action = 'ALLERGY_ADDED' and patient_id = ?", patient);
        assertThat(event).containsEntry("outcome", "SUCCESS").containsEntry("resource_type", "ALLERGY")
            .containsEntry("resource_id", String.valueOf(view.allergyId())).containsEntry("severity", "MILD");
        assertThat((String) event.get("details")).doesNotContain("Secret");
    }

    @Test
    void anActiveCopyIsRefusedWhateverTheLetterCaseButHistoryDoesNotCount() {
        long patient = newPatient();
        long doctor = newDoctor();
        AllergyView first = service.add(doctor, patient, "Latex", null, AllergySeverity.MILD);

        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.add(doctor, patient, "  LATEX ", null, AllergySeverity.SEVERE)), ErrorCode.CONFLICT);

        service.changeStatus(first.allergyId(), first.version(), AllergyStatus.INACTIVE, "Outgrown");
        assertThat(service.add(doctor, patient, "Latex", null, AllergySeverity.MILD).allergyId()).isPositive();
    }

    @Test
    void anUnknownPatientAndBadInputAreRefused() {
        long doctor = newDoctor();
        long patient = newPatient();

        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.add(doctor, 999_999_999L, "Latex", null, AllergySeverity.MILD)), ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.add(doctor, patient, "   ", null, AllergySeverity.MILD)), ErrorCode.VALIDATION_FAILED);
        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.add(doctor, patient, "Latex", null, null)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void markingInactiveNeedsAReasonAndTheAuditEntryHasStatusBeforeAndAfterButNotTheReason() {
        long patient = newPatient();
        AllergyView added = service.add(newDoctor(), patient, "Aspirin", null, AllergySeverity.MODERATE);

        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(added.allergyId(), added.version(), AllergyStatus.INACTIVE, "  ")), ErrorCode.VALIDATION_FAILED);
        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(added.allergyId(), added.version(), AllergyStatus.INACTIVE, "x".repeat(201))),
            ErrorCode.VALIDATION_FAILED);

        AllergyView inactive = service.changeStatus(added.allergyId(), added.version(), AllergyStatus.INACTIVE,
            "Zzz-Secret-Reason");

        assertThat(inactive.status()).isEqualTo(AllergyStatus.INACTIVE);
        assertThat(inactive.statusReason()).isEqualTo("Zzz-Secret-Reason");
        assertThat(inactive.version()).isEqualTo(added.version() + 1);
        Map<String, Object> event = jdbc.queryForMap("select details ->> 'statusBefore' as before_status, "
            + "details ->> 'statusAfter' as after_status, details::text as details from audit_event "
            + "where action = 'ALLERGY_STATUS_CHANGED' and patient_id = ?", patient);
        assertThat(event).containsEntry("before_status", "ACTIVE").containsEntry("after_status", "INACTIVE");
        assertThat((String) event.get("details")).doesNotContain("Secret");
    }

    @Test
    void anAllergyEnteredInErrorStaysOnRecordAndCanNotBeChangedAgain() {
        long patient = newPatient();
        AllergyView added = service.add(newDoctor(), patient, "Peanut", null, AllergySeverity.SEVERE);

        AllergyView error = service.changeStatus(added.allergyId(), added.version(), AllergyStatus.ENTERED_IN_ERROR, "Wrong patient");

        assertThat(jdbc.queryForObject("select count(*) from allergy where allergy_id = ?", Long.class, added.allergyId()))
            .isEqualTo(1L);
        for (AllergyStatus target : new AllergyStatus[] {AllergyStatus.ACTIVE, AllergyStatus.INACTIVE}) {
            assertCode(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.changeStatus(error.allergyId(), error.version(), target, "reason")), ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    @Test
    void anInactiveAllergyCanBeActivatedAgainUnlessTheSubstanceIsActiveMeanwhile() {
        long patient = newPatient();
        long doctor = newDoctor();
        AllergyView first = service.add(doctor, patient, "Sulfa", null, AllergySeverity.MILD);
        AllergyView inactive = service.changeStatus(first.allergyId(), first.version(), AllergyStatus.INACTIVE, "Outgrown");

        AllergyView again = service.changeStatus(inactive.allergyId(), inactive.version(), AllergyStatus.ACTIVE, null);
        assertThat(again.status()).isEqualTo(AllergyStatus.ACTIVE);

        AllergyView off = service.changeStatus(again.allergyId(), again.version(), AllergyStatus.INACTIVE, "Retested");
        service.add(doctor, patient, "Sulfa", null, AllergySeverity.MODERATE);
        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(off.allergyId(), off.version(), AllergyStatus.ACTIVE, null)), ErrorCode.CONFLICT);
    }

    @Test
    void theSameStatusIsRefused() {
        AllergyView added = service.add(newDoctor(), newPatient(), "Pollen", null, AllergySeverity.MILD);

        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(added.allergyId(), added.version(), AllergyStatus.ACTIVE, null)),
            ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void aStaleVersionAndAnUnknownAllergyAreRefused() {
        AllergyView added = service.add(newDoctor(), newPatient(), "Dust", null, AllergySeverity.MILD);
        service.changeStatus(added.allergyId(), added.version(), AllergyStatus.INACTIVE, "Outgrown");

        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(added.allergyId(), added.version(), AllergyStatus.ENTERED_IN_ERROR, "Wrong")),
            ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        assertThat(jdbc.queryForObject("select status from allergy where allergy_id = ?", String.class, added.allergyId()))
            .isEqualTo("INACTIVE");
        assertCode(org.assertj.core.api.Assertions.catchThrowable(
            () -> service.changeStatus(999_999_999L, 0, AllergyStatus.INACTIVE, "x")), ErrorCode.RESOURCE_NOT_FOUND);
    }
}
