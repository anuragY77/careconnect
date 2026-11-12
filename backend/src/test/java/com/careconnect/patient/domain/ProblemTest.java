package com.careconnect.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ProblemTest {

    private static Problem problem() {
        return Problem.record(1L, "E11.9", "Type 2 diabetes mellitus without complications", LocalDate.of(2020, 3, 1), 7L);
    }

    @Test
    void aNewProblemIsActive() {
        Problem p = problem();

        assertThat(p.getStatus()).isEqualTo(ProblemStatus.ACTIVE);
        assertThat(p.getStatusReason()).isNull();
        assertThat(p.getRecordedBy()).isEqualTo(7L);
        assertThat(p.getOnsetDate()).isEqualTo(LocalDate.of(2020, 3, 1));
    }

    @Test
    void requiredValuesAreChecked() {
        assertThatThrownBy(() -> Problem.record(1L, " ", "Text", null, 7L)).hasMessageContaining("icd10Code");
        assertThatThrownBy(() -> Problem.record(1L, "I10", null, null, 7L)).hasMessageContaining("description");
    }

    @Test
    void resolvingNeedsNoReasonButEnteringInErrorDoes() {
        Problem p = problem();

        p.changeStatus(ProblemStatus.RESOLVED, null);
        assertThat(p.getStatus()).isEqualTo(ProblemStatus.RESOLVED);
        p.changeStatus(ProblemStatus.ACTIVE, null);
        assertThatThrownBy(() -> p.changeStatus(ProblemStatus.ENTERED_IN_ERROR, " ")).hasMessageContaining("reason");
        p.changeStatus(ProblemStatus.ENTERED_IN_ERROR, "Wrong patient");
        assertThat(p.getStatusReason()).isEqualTo("Wrong patient");
    }

    @Test
    void aChangeThatTheRulesForbidIsRefused() {
        Problem p = problem();
        p.changeStatus(ProblemStatus.ENTERED_IN_ERROR, "Wrong patient");

        assertThatThrownBy(() -> p.changeStatus(ProblemStatus.ACTIVE, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> problem().changeStatus(ProblemStatus.ACTIVE, null)).isInstanceOf(IllegalStateException.class);
    }
}
