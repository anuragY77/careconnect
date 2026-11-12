package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** One active allergy per substance and patient, enforced by the database (V7). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AllergyActiveUniquenessIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aSecondActiveAllergyForTheSameSubstanceIsRefusedWhateverTheLetterCase() {
        long patient = jdbc.queryForObject("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('T' || nextval('patient_mrn_seq'), 'A', 'B', date '1990-01-01', 'FEMALE') returning patient_id", Long.class);
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long user = jdbc.queryForObject("insert into app_user (username, password_hash, full_name, email, role) "
            + "values (?, 'hash', 'Dr X', ?, 'DOCTOR') returning user_id", Long.class, name, name + "@example.org");
        jdbc.update("insert into allergy (patient_id, substance, severity, recorded_by) values (?, 'Penicillin', 'SEVERE', ?)",
            patient, user);

        assertThatThrownBy(() -> jdbc.update(
            "insert into allergy (patient_id, substance, severity, recorded_by) values (?, 'PENICILLIN', 'MILD', ?)", patient, user))
            .rootCause().hasMessageContaining("uq_allergy_active_substance");

        // History does not count: once it is no longer active the substance can be recorded again.
        jdbc.update("update allergy set status = 'INACTIVE', status_reason = 'Outgrown' where patient_id = ?", patient);
        assertThat(jdbc.update(
            "insert into allergy (patient_id, substance, severity, recorded_by) values (?, 'Penicillin', 'MILD', ?)", patient, user))
            .isEqualTo(1);
    }
}
