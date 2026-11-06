package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PatientSearchQueryTest {

    @Test
    void wordsAreANameSearchInLowerCase() {
        assertThat(PatientSearchQuery.parse("  Rao   ASHA ")).isEqualTo(new PatientSearchQuery.ByName(List.of("rao", "asha")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC-100001", "cc-100001", "CC100001", "100001"})
    void anMrnIsRecognisedInEveryWayItIsTyped(String text) {
        assertThat(PatientSearchQuery.parse(text)).isEqualTo(new PatientSearchQuery.ByMrn("CC-100001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1990-02-05", "05/02/1990", "5.2.1990", "5-2-1990"})
    void aDateOfBirthIsReadDayFirst(String text) {
        assertThat(PatientSearchQuery.parse(text))
            .isEqualTo(new PatientSearchQuery.ByDateOfBirth(LocalDate.of(1990, 2, 5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"98765 43210", "+91 98765-43210", "(98765) 43210"})
    void aPhoneNumberKeepsOnlyItsDigits(String text) {
        assertThat(PatientSearchQuery.parse(text)).isInstanceOfSatisfying(PatientSearchQuery.ByPhone.class,
            p -> assertThat(p.digits()).matches("(91)?9876543210"));
    }

    @Test
    void aNumberThatIsNotARealDateMayStillBeAPhoneNumber() {
        assertThat(PatientSearchQuery.parse("1234-56-78")).isEqualTo(new PatientSearchQuery.ByPhone("12345678"));
    }

    @Test
    void aShortNumberIsJustAWord() {
        assertThat(PatientSearchQuery.parse("12345")).isEqualTo(new PatientSearchQuery.ByName(List.of("12345")));
    }

    @Test
    void emptyOrOversizedSearchesAreRefused() {
        for (String bad : new String[] {null, "", "   ", "x".repeat(101), "a b c d e f"}) {
            assertThatThrownBy(() -> PatientSearchQuery.parse(bad))
                .as("search text: " + bad)
                .isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }
}
