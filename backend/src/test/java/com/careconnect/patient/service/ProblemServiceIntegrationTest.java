package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.ProblemStatus;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProblemServiceIntegrationTest {

    @Autowired
    private ProblemService service;

    @Autowired
    private PatientService patients;

    @Autowired
    private JdbcTemplate jdbc;

    private long newPatient() {
        String last = "Pr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return patients.register(new NewPatient("Asha", last, LocalDate.of(1990, 1, 1), Sex.FEMALE, null, null,
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
    void addsAnActiveProblemWithTheCatalogueDescriptionRecordedByTheSignedInUser() {
        long patient = newPatient();
        long doctor = newDoctor();

        ProblemView view = service.add(doctor, patient, " e11.9 ", LocalDate.of(2020, 3, 1));

        assertThat(view.icd10Code()).isEqualTo("E11.9");
        assertThat(view.description()).isEqualTo("Type 2 diabetes mellitus without complications");
        assertThat(view.onsetDate()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(view.status()).isEqualTo(ProblemStatus.ACTIVE);
        assertThat(view.recordedBy()).isEqualTo(doctor);
        assertThat(view.patientId()).isEqualTo(patient);
        assertThat(view.version()).isZero();
    }

    @Test
    void addingIsAuditedWithoutTheDiagnosis() {
        long patient = newPatient();

        ProblemView view = service.add(newDoctor(), patient, "F32.9", null);

        Map<String, Object> event = jdbc.queryForMap("select outcome, resource_type, resource_id, "
            + "coalesce(details::text, '') as details from audit_event where action = 'PROBLEM_ADDED' and patient_id = ?", patient);
        assertThat(event).containsEntry("outcome", "SUCCESS").containsEntry("resource_type", "PROBLEM")
            .containsEntry("resource_id", String.valueOf(view.problemId()));
        assertThat((String) event.get("details")).doesNotContain("F32");
    }

    @Test
    void anUnknownCodeAnUnknownPatientAndABadOnsetDateAreRefused() {
        long doctor = newDoctor();
        long patient = newPatient();

        assertCode(catchThrowable(() -> service.add(doctor, patient, "Z99.9", null)), ErrorCode.VALIDATION_FAILED);
        assertCode(catchThrowable(() -> service.add(doctor, patient, null, null)), ErrorCode.VALIDATION_FAILED);
        assertCode(catchThrowable(() -> service.add(doctor, 999_999_999L, "I10", null)), ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(catchThrowable(() -> service.add(doctor, patient, "I10", LocalDate.now().plusDays(5))),
            ErrorCode.VALIDATION_FAILED);
        assertCode(catchThrowable(() -> service.add(doctor, patient, "I10", LocalDate.of(1899, 12, 31))),
            ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void anActiveCopyIsRefusedButAResolvedOneDoesNotCount() {
        long patient = newPatient();
        long doctor = newDoctor();
        ProblemView first = service.add(doctor, patient, "I10", null);

        assertCode(catchThrowable(() -> service.add(doctor, patient, "i10", null)), ErrorCode.CONFLICT);

        service.changeStatus(first.problemId(), first.version(), ProblemStatus.RESOLVED, null);
        assertThat(service.add(doctor, patient, "I10", null).problemId()).isPositive();
    }

    @Test
    void resolvingNeedsNoReasonAndTheAuditEntryHasStatusBeforeAndAfter() {
        long patient = newPatient();
        ProblemView added = service.add(newDoctor(), patient, "J45.909", null);

        ProblemView resolved = service.changeStatus(added.problemId(), added.version(), ProblemStatus.RESOLVED, "  ");

        assertThat(resolved.status()).isEqualTo(ProblemStatus.RESOLVED);
        assertThat(resolved.statusReason()).isNull();
        assertThat(resolved.version()).isEqualTo(added.version() + 1);
        Map<String, Object> event = jdbc.queryForMap("select details ->> 'statusBefore' as before_status, "
            + "details ->> 'statusAfter' as after_status from audit_event "
            + "where action = 'PROBLEM_STATUS_CHANGED' and patient_id = ?", patient);
        assertThat(event).containsEntry("before_status", "ACTIVE").containsEntry("after_status", "RESOLVED");
    }

    @Test
    void enteringInErrorNeedsAReasonAndIsFinalButTheRowStays() {
        long patient = newPatient();
        ProblemView added = service.add(newDoctor(), patient, "K21.9", null);

        assertCode(catchThrowable(() -> service.changeStatus(added.problemId(), added.version(),
            ProblemStatus.ENTERED_IN_ERROR, " ")), ErrorCode.VALIDATION_FAILED);
        assertCode(catchThrowable(() -> service.changeStatus(added.problemId(), added.version(),
            ProblemStatus.ENTERED_IN_ERROR, "x".repeat(201))), ErrorCode.VALIDATION_FAILED);

        ProblemView error = service.changeStatus(added.problemId(), added.version(), ProblemStatus.ENTERED_IN_ERROR,
            "Wrong patient");

        assertThat(error.statusReason()).isEqualTo("Wrong patient");
        assertThat(jdbc.queryForObject("select count(*) from problem where problem_id = ?", Long.class, added.problemId()))
            .isEqualTo(1L);
        for (ProblemStatus target : new ProblemStatus[] {ProblemStatus.ACTIVE, ProblemStatus.RESOLVED}) {
            assertCode(catchThrowable(() -> service.changeStatus(error.problemId(), error.version(), target, "reason")),
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    @Test
    void aResolvedProblemCanComeBackUnlessTheSameProblemIsActiveMeanwhile() {
        long patient = newPatient();
        long doctor = newDoctor();
        ProblemView first = service.add(doctor, patient, "J06.9", null);
        ProblemView resolved = service.changeStatus(first.problemId(), first.version(), ProblemStatus.RESOLVED, null);

        ProblemView again = service.changeStatus(resolved.problemId(), resolved.version(), ProblemStatus.ACTIVE, null);
        assertThat(again.status()).isEqualTo(ProblemStatus.ACTIVE);

        ProblemView off = service.changeStatus(again.problemId(), again.version(), ProblemStatus.RESOLVED, null);
        service.add(doctor, patient, "J06.9", null);
        assertCode(catchThrowable(() -> service.changeStatus(off.problemId(), off.version(), ProblemStatus.ACTIVE, null)),
            ErrorCode.CONFLICT);
    }

    @Test
    void theSameStatusIsRefused() {
        ProblemView added = service.add(newDoctor(), newPatient(), "R51.9", null);

        assertCode(catchThrowable(() -> service.changeStatus(added.problemId(), added.version(), ProblemStatus.ACTIVE, null)),
            ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void aStaleVersionAndAnUnknownProblemAreRefused() {
        ProblemView added = service.add(newDoctor(), newPatient(), "R50.9", null);
        service.changeStatus(added.problemId(), added.version(), ProblemStatus.RESOLVED, null);

        assertCode(catchThrowable(() -> service.changeStatus(added.problemId(), added.version(),
            ProblemStatus.ENTERED_IN_ERROR, "Wrong")), ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        assertThat(jdbc.queryForObject("select status from problem where problem_id = ?", String.class, added.problemId()))
            .isEqualTo("RESOLVED");
        assertCode(catchThrowable(() -> service.changeStatus(999_999_999L, 0, ProblemStatus.RESOLVED, null)),
            ErrorCode.RESOURCE_NOT_FOUND);
    }
}
