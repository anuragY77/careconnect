package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

/** Registration rules against a real PostgreSQL (TC-10, TC-11). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientServiceIntegrationTest {

    private static final LocalDate DOB = LocalDate.of(1988, 7, 21);

    @Autowired
    private PatientService service;

    @Autowired
    private JdbcTemplate jdbc;

    /** A name no other test uses, so tests cannot trigger each other's duplicate warning. */
    private static String unique() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static NewPatient patient(String first, String last, LocalDate dob, String phone) {
        return new NewPatient(first, last, dob, Sex.FEMALE, phone, null, null, null, null, null, null);
    }

    private PatientView register(NewPatient input) {
        return service.register(input, false);
    }

    private long patientCount() {
        return jdbc.queryForObject("select count(*) from patient", Long.class);
    }

    private static ApiException duplicateWarning(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException e = (ApiException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POSSIBLE_DUPLICATE);
        return e;
    }

    @Test
    void registersWithAGeneratedMrnAndCleansTheInput() {
        String last = unique();
        NewPatient input = new NewPatient("  Asha ", last + "   Singh", LocalDate.of(1990, 5, 17), Sex.FEMALE,
            " 98765 43210 ", "ASHA@Example.ORG", "  ", "  Ravi Rao ", "", " Star Health ", " P-123 ");

        PatientView view = register(input);

        assertThat(view.mrn()).matches("CC-\\d{6,}");
        assertThat(view.firstName()).isEqualTo("Asha");
        assertThat(view.lastName()).isEqualTo(last + " Singh");
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
        PatientView a = register(patient(unique(), unique(), DOB, null));
        PatientView b = register(patient(unique(), unique(), DOB, null));

        assertThat(a.mrn()).isNotEqualTo(b.mrn());
        assertThat(Long.parseLong(b.mrn().substring(3))).isGreaterThan(Long.parseLong(a.mrn().substring(3)));
    }

    @Test
    void theDateOfBirthMustBePlausible() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        long before = patientCount();

        assertThatThrownBy(() -> register(patient("A", "B", todayUtc.plusDays(2), null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> register(patient("A", "B", LocalDate.of(1899, 12, 31), null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(patientCount()).isEqualTo(before);

        // Born just after midnight in India: still tomorrow in UTC (V6).
        assertThat(register(patient(unique(), unique(), todayUtc.plusDays(1), null)).patientId()).isPositive();
        assertThat(register(patient(unique(), unique(), todayUtc, null)).patientId()).isPositive();
    }

    @Test
    void namesAreRequired() {
        assertThatThrownBy(() -> register(patient("   ", "B", DOB, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> register(patient("A", null, DOB, null)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void registrationIsAudited() {
        PatientView view = register(patient(unique(), unique(), LocalDate.of(1985, 3, 3), null));

        Map<String, Object> event = jdbc.queryForMap("select action, outcome, patient_id, resource_type, resource_id, "
            + "details ->> 'mrn' as mrn, details::text as details from audit_event "
            + "where action = 'PATIENT_REGISTERED' and patient_id = ?", view.patientId());
        assertThat(event).containsEntry("outcome", "SUCCESS").containsEntry("resource_type", "PATIENT")
            .containsEntry("resource_id", String.valueOf(view.patientId())).containsEntry("mrn", view.mrn());
        // Identifiers only: the audit trail never carries names.
        assertThat((String) event.get("details")).doesNotContain(view.firstName()).doesNotContain(view.lastName())
            .doesNotContain("duplicateConfirmed");
    }

    // ---- possible duplicates (TC-11) ------------------------------------------------

    @Test
    void sameNameAndDateOfBirthIsWarnedAboutAndNothingIsSaved() {
        String first = unique();
        String last = unique();
        PatientView existing = register(patient(first, last, DOB, null));
        long before = patientCount();

        // Letter case does not matter.
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
            () -> register(patient(first.toLowerCase(), last.toUpperCase(), DOB, null)));

        ApiException warning = duplicateWarning(thrown);
        @SuppressWarnings("unchecked")
        List<PossibleDuplicate> list = (List<PossibleDuplicate>) warning.getProperties().get("possibleDuplicates");
        assertThat(list).extracting(PossibleDuplicate::mrn).containsExactly(existing.mrn());
        assertThat(patientCount()).isEqualTo(before);
    }

    @Test
    void samePhoneAndDateOfBirthIsWarnedAboutWhateverTheFormatting() {
        String phone = String.format("9%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        PatientView existing = register(patient(unique(), unique(), DOB, phone));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
            () -> register(patient(unique(), unique(), DOB, "+" + phone.substring(0, 5) + "-" + phone.substring(5))));

        @SuppressWarnings("unchecked")
        List<PossibleDuplicate> list = (List<PossibleDuplicate>) duplicateWarning(thrown).getProperties().get("possibleDuplicates");
        assertThat(list).extracting(PossibleDuplicate::mrn).contains(existing.mrn());
    }

    @Test
    void differentDateOfBirthOrAShortNumberIsNotADuplicate() {
        String first = unique();
        String last = unique();
        register(patient(first, last, DOB, null));

        // The same name with another date of birth is another person.
        assertThat(register(patient(first, last, DOB.plusDays(1), null)).patientId()).isPositive();
        // Fewer than 7 digits is not a phone number: two people sharing it are not compared.
        register(patient(unique(), unique(), DOB, "123456"));
        assertThat(register(patient(unique(), unique(), DOB, "123456")).patientId()).isPositive();
    }

    @Test
    void confirmingRegistersAnywayAndTheAuditEntryRecordsIt() {
        String first = unique();
        String last = unique();
        PatientView existing = register(patient(first, last, DOB, null));

        PatientView twin = service.register(patient(first, last, DOB, null), true);

        assertThat(twin.mrn()).isNotEqualTo(existing.mrn());
        Map<String, Object> event = jdbc.queryForMap("select details ->> 'duplicateConfirmed' as confirmed, "
            + "details ->> 'similarMrns' as similar from audit_event "
            + "where action = 'PATIENT_REGISTERED' and patient_id = ?", twin.patientId());
        assertThat(event).containsEntry("confirmed", "true").containsEntry("similar", existing.mrn());
    }
}
