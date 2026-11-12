package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProblemSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long patient() {
        return jdbc.queryForObject("insert into patient (mrn, first_name, last_name, dob, sex) "
            + "values ('T' || nextval('patient_mrn_seq'), 'A', 'B', date '1990-01-01', 'FEMALE') returning patient_id", Long.class);
    }

    private long user() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("insert into app_user (username, password_hash, full_name, email, role) "
            + "values (?, 'hash', 'Dr X', ?, 'DOCTOR') returning user_id", Long.class, name, name + "@example.org");
    }

    @Test
    void theCatalogueHoldsTheDemoSubsetAndOnlyWellFormedCodes() {
        assertThat(jdbc.queryForObject("select count(*) from icd10_code", Long.class)).isGreaterThanOrEqualTo(50L);
        assertThat(jdbc.queryForObject("select description from icd10_code where code = 'I10'", String.class))
            .isEqualTo("Essential (primary) hypertension");
        assertThatThrownBy(() -> jdbc.update("insert into icd10_code (code, description) values ('bad', 'x')"))
            .rootCause().hasMessageContaining("ck_icd10_code_format");
    }

    @Test
    void aProblemNeedsACodeFromTheCatalogueAndOnlyOneActiveRowPerCode() {
        long patient = patient();
        long user = user();

        assertThatThrownBy(() -> jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) "
            + "values (?, 'Z99.9', 'Unknown', ?)", patient, user)).rootCause().hasMessageContaining("fk_problem_icd10");

        jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) values (?, 'I10', 'Hypertension', ?)",
            patient, user);
        assertThatThrownBy(() -> jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) "
            + "values (?, 'I10', 'Hypertension', ?)", patient, user)).rootCause().hasMessageContaining("uq_problem_active_code");

        jdbc.update("update problem set status = 'RESOLVED' where patient_id = ?", patient);
        assertThat(jdbc.update("insert into problem (patient_id, icd10_code, description, recorded_by) "
            + "values (?, 'I10', 'Hypertension', ?)", patient, user)).isEqualTo(1);
    }
}
