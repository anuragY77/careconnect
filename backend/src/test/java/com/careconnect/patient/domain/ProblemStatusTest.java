package com.careconnect.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProblemStatusTest {

    @Test
    void anActiveProblemCanBeResolvedOrEnteredInError() {
        assertThat(ProblemStatus.ACTIVE.canChangeTo(ProblemStatus.RESOLVED)).isTrue();
        assertThat(ProblemStatus.ACTIVE.canChangeTo(ProblemStatus.ENTERED_IN_ERROR)).isTrue();
        assertThat(ProblemStatus.ACTIVE.canChangeTo(ProblemStatus.ACTIVE)).isFalse();
    }

    @Test
    void aResolvedProblemCanComeBackOrBeEnteredInError() {
        assertThat(ProblemStatus.RESOLVED.canChangeTo(ProblemStatus.ACTIVE)).isTrue();
        assertThat(ProblemStatus.RESOLVED.canChangeTo(ProblemStatus.ENTERED_IN_ERROR)).isTrue();
        assertThat(ProblemStatus.RESOLVED.canChangeTo(ProblemStatus.RESOLVED)).isFalse();
    }

    @Test
    void aProblemEnteredInErrorIsFinal() {
        for (ProblemStatus target : ProblemStatus.values()) {
            assertThat(ProblemStatus.ENTERED_IN_ERROR.canChangeTo(target)).as("to " + target).isFalse();
        }
    }
}
