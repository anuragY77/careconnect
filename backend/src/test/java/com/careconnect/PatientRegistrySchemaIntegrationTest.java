package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The patient registry tables against a real PostgreSQL. The rules live in the database, so they
 * hold for every caller. Each test checks the constraint name, so a failure for another reason
 * cannot pass by accident.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientRegistrySchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long patient() {
        return jdbc.queryForObject("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('T' || nextval('patient_mrn_seq'), 'Asha', 'Rao', date '1990-05-17', 'FEMALE') "
            + "returning patient_id", Long.class);
    }

    private long user() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("insert into app_user (username, password_hash, full_name, email, role) "
            + "values (?, 'hash', 'Dr X', ?, 'DOCTOR') returning user_id", Long.class, name, name + "@example.org");
    }

    @Test
    void mrnSequenceStartsHighAndGrows() {
        long a = jdbc.queryForObject("select nextval('patient_mrn_seq')", Long.class);
        long b = jdbc.queryForObject("select nextval('patient_mrn_seq')", Long.class);

        assertThat(a).isGreaterThanOrEqualTo(100001L);
        assertThat(b).isGreaterThan(a);
    }

    @Test
    void mrnIsUniqueAndNeverChanges() {
        long id = patient();
        String mrn = jdbc.queryForObject("select mrn from patient where patient_id = ?", String.class, id);

        assertThatThrownBy(() -> jdbc.update(
            "insert into patient (mrn, first_name, last_name, dob, sex) values (?, 'B', 'C', date '2000-01-01', 'MALE')", mrn))
            .rootCause().hasMessageContaining("uq_patient_mrn");
        assertThatThrownBy(() -> jdbc.update("update patient set mrn = 'OTHER-1' where patient_id = ?", id))
            .rootCause().hasMessageContaining("MRN of a patient can never change");
        // Other fields still change, and an update that keeps the MRN is fine.
        assertThat(jdbc.update("update patient set phone = '9876543210' where patient_id = ?", id)).isEqualTo(1);
    }

    @Test
    void patientRulesAreEnforcedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('X1', 'A', 'B', current_date + 1, 'MALE')")).rootCause().hasMessageContaining("ck_patient_dob");
        assertThatThrownBy(() -> jdbc.update("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('X2', 'A', 'B', date '2000-01-01', 'ROBOT')")).rootCause().hasMessageContaining("ck_patient_sex");
        assertThatThrownBy(() -> jdbc.update("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('X3', '  ', 'B', date '2000-01-01', 'MALE')")).rootCause().hasMessageContaining("ck_patient_names");
    }

    @Test
    void clinicalRowsCanNotBeDeleted() {
        long patient = patient();
        long user = user();
        jdbc.update("insert into allergy (patient_id, substance, severity, recorded_by) values (?, 'Penicillin', 'SEVERE', ?)",
            patient, user);
        jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) values (?, 'E11.9', 'Type 2 diabetes', ?)",
            patient, user);

        assertThatThrownBy(() -> jdbc.update("delete from allergy where patient_id = ?", patient))
            .rootCause().hasMessageContaining("never deleted");
        assertThatThrownBy(() -> jdbc.update("delete from problem where patient_id = ?", patient))
            .rootCause().hasMessageContaining("never deleted");
        assertThatThrownBy(() -> jdbc.update("delete from patient where patient_id = ?", patient))
            .rootCause().hasMessageContaining("never deleted");
    }

    @Test
    void allergyNeedsAReasonToLeaveActive() {
        long patient = patient();
        long user = user();
        long allergy = jdbc.queryForObject("insert into allergy (patient_id, substance, severity, recorded_by) "
            + "values (?, 'Latex', 'MILD', ?) returning allergy_id", Long.class, patient, user);

        assertThatThrownBy(() -> jdbc.update("update allergy set status = 'INACTIVE' where allergy_id = ?", allergy))
            .rootCause().hasMessageContaining("ck_allergy_status_reason");
        assertThat(jdbc.update("update allergy set status = 'ENTERED_IN_ERROR', status_reason = 'Wrong patient' "
            + "where allergy_id = ?", allergy)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into allergy (patient_id, substance, severity, recorded_by) "
            + "values (?, 'Dust', 'EXTREME', ?)", patient, user)).rootCause().hasMessageContaining("ck_allergy_severity");
    }

    @Test
    void problemCodeAndStatusRulesHold() {
        long patient = patient();
        long user = user();

        assertThatThrownBy(() -> jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) "
            + "values (?, 'not-a-code', 'x', ?)", patient, user)).rootCause().hasMessageContaining("ck_problem_icd10");
        long problem = jdbc.queryForObject("insert into problem (patient_id, icd10_code, description, recorded_by) "
            + "values (?, 'I10', 'Hypertension', ?) returning problem_id", Long.class, patient, user);
        assertThat(jdbc.update("update problem set status = 'RESOLVED' where problem_id = ?", problem)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update problem set status = 'ENTERED_IN_ERROR' where problem_id = ?", problem))
            .rootCause().hasMessageContaining("ck_problem_status_reason");
    }

    @Test
    void aPortalAccountMustPointAtARealPatient() {
        long patient = patient();
        String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        assertThatThrownBy(() -> jdbc.update("insert into app_user (username, password_hash, full_name, email, role, linked_patient_id) "
            + "values (?, 'hash', 'P', ?, 'PATIENT', 999999999)", name, name + "@example.org"))
            .rootCause().hasMessageContaining("fk_app_user_linked_patient");
        assertThat(jdbc.update("insert into app_user (username, password_hash, full_name, email, role, linked_patient_id) "
            + "values (?, 'hash', 'P', ?, 'PATIENT', ?)", name, name + "@example.org", patient)).isEqualTo(1);
    }
}
