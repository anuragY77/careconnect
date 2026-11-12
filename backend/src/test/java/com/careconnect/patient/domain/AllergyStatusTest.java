package com.careconnect.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AllergyStatusTest {

    @Test
    void anActiveAllergyCanBecomeInactiveOrEnteredInError() {
        assertThat(AllergyStatus.ACTIVE.canChangeTo(AllergyStatus.INACTIVE)).isTrue();
        assertThat(AllergyStatus.ACTIVE.canChangeTo(AllergyStatus.ENTERED_IN_ERROR)).isTrue();
        assertThat(AllergyStatus.ACTIVE.canChangeTo(AllergyStatus.ACTIVE)).isFalse();
    }

    @Test
    void anInactiveAllergyCanBecomeActiveAgainOrEnteredInError() {
        assertThat(AllergyStatus.INACTIVE.canChangeTo(AllergyStatus.ACTIVE)).isTrue();
        assertThat(AllergyStatus.INACTIVE.canChangeTo(AllergyStatus.ENTERED_IN_ERROR)).isTrue();
        assertThat(AllergyStatus.INACTIVE.canChangeTo(AllergyStatus.INACTIVE)).isFalse();
    }

    @Test
    void anAllergyEnteredInErrorIsFinal() {
        for (AllergyStatus target : AllergyStatus.values()) {
            assertThat(AllergyStatus.ENTERED_IN_ERROR.canChangeTo(target)).as("to " + target).isFalse();
        }
    }
}
