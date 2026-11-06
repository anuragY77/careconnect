package com.careconnect.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PatientTest {

    private static Patient with(String mrn, String first, String last, LocalDate dob, Sex sex) {
        return Patient.register(mrn, first, last, dob, sex, null, null, null, null, null, null, null);
    }

    @Test
    void aNewPatientIsActive() {
        Patient p = with("CC-100001", "Asha", "Rao", LocalDate.of(1990, 5, 17), Sex.FEMALE);

        assertThat(p.getStatus()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(p.getMrn()).isEqualTo("CC-100001");
    }

    @Test
    void requiredValuesAreChecked() {
        LocalDate dob = LocalDate.of(1990, 5, 17);

        assertThatThrownBy(() -> with(" ", "A", "B", dob, Sex.MALE)).hasMessageContaining("mrn");
        assertThatThrownBy(() -> with("M", "", "B", dob, Sex.MALE)).hasMessageContaining("firstName");
        assertThatThrownBy(() -> with("M", "A", null, dob, Sex.MALE)).hasMessageContaining("lastName");
        assertThatThrownBy(() -> with("M", "A", "B", null, Sex.MALE)).hasMessageContaining("dateOfBirth");
        assertThatThrownBy(() -> with("M", "A", "B", dob, null)).hasMessageContaining("sex");
    }
}
