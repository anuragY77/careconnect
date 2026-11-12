package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class Icd10ServiceIntegrationTest {

    @Autowired
    private Icd10Service service;

    @Test
    void findsByCodePrefixWhateverTheLetterCase() {
        List<Icd10View> found = service.search("  e11 ");

        assertThat(found).extracting(Icd10View::code).contains("E11.9");
    }

    @Test
    void findsByAnyPartOfTheDescriptionAndReturnsAtMostTwenty() {
        assertThat(service.search("HYPERTENS")).extracting(Icd10View::code).contains("I10");
        assertThat(service.search("ed")).hasSize(20);
    }

    @Test
    void tooShortOrTooLongTextAndWildcardsAreHandled() {
        for (String bad : new String[] {null, "", " a ", "x".repeat(61)}) {
            assertThat(catchThrowable(() -> service.search(bad))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(service.search("%%")).isEmpty();
        assertThat(service.search("__")).isEmpty();
    }
}
