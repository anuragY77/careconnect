package com.careconnect.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AllergyTest {

    private static Allergy allergy() {
        return Allergy.record(1L, "Penicillin", "Rash", AllergySeverity.SEVERE, 7L);
    }

    @Test
    void aNewAllergyIsActive() {
        Allergy a = allergy();

        assertThat(a.getStatus()).isEqualTo(AllergyStatus.ACTIVE);
        assertThat(a.getStatusReason()).isNull();
        assertThat(a.getRecordedBy()).isEqualTo(7L);
    }

    @Test
    void requiredValuesAreChecked() {
        assertThatThrownBy(() -> Allergy.record(1L, " ", null, AllergySeverity.MILD, 7L)).hasMessageContaining("substance");
        assertThatThrownBy(() -> Allergy.record(1L, "Latex", null, null, 7L)).hasMessageContaining("severity");
    }

    @Test
    void leavingActiveNeedsAReasonAndBecomingActiveDoesNot() {
        Allergy a = allergy();

        assertThatThrownBy(() -> a.changeStatus(AllergyStatus.INACTIVE, " ")).hasMessageContaining("reason");
        a.changeStatus(AllergyStatus.INACTIVE, "Outgrown");
        assertThat(a.getStatus()).isEqualTo(AllergyStatus.INACTIVE);
        assertThat(a.getStatusReason()).isEqualTo("Outgrown");
        a.changeStatus(AllergyStatus.ACTIVE, null);
        assertThat(a.getStatus()).isEqualTo(AllergyStatus.ACTIVE);
    }

    @Test
    void aChangeThatTheRulesForbidIsRefused() {
        Allergy a = allergy();
        a.changeStatus(AllergyStatus.ENTERED_IN_ERROR, "Wrong patient");

        assertThatThrownBy(() -> a.changeStatus(AllergyStatus.ACTIVE, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> allergy().changeStatus(AllergyStatus.ACTIVE, null)).isInstanceOf(IllegalStateException.class);
    }
}
