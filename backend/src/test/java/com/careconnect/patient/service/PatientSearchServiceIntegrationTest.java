package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.Sex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Patient search against a real PostgreSQL (TC-12, TC-13). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PatientSearchServiceIntegrationTest {

    @Autowired
    private PatientService registration;

    @Autowired
    private PatientSearchService search;

    private static String unique() {
        return "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private PatientView register(String first, String last, LocalDate dob, String phone) {
        return registration.register(new NewPatient(first, last, dob, Sex.FEMALE, phone, null, null, null, null, null, null), false);
    }

    private List<String> mrns(String text) {
        return search.search(text, 0, 50).map(PatientSummary::mrn).getContent();
    }

    @Test
    void findsByNameInAnyOrderByPrefixIgnoringCase() {
        String last = unique();
        PatientView asha = register("Asha", last, LocalDate.of(1990, 1, 1), null);
        register("Meera", last, LocalDate.of(1991, 1, 1), null);

        assertThat(mrns(last.toUpperCase())).hasSize(2);
        assertThat(mrns("asha " + last.toLowerCase())).containsExactly(asha.mrn());
        assertThat(mrns(last.toLowerCase() + " ash")).containsExactly(asha.mrn());
        assertThat(mrns(last.substring(0, 8))).contains(asha.mrn());
        // The middle of a name is not a match: this is a prefix search.
        assertThat(mrns(last.substring(1))).isEmpty();
    }

    @Test
    void findsByMrnInEveryWayItIsTyped() {
        PatientView p = register(unique(), unique(), LocalDate.of(1990, 1, 1), null);
        String number = p.mrn().substring(3);

        assertThat(mrns(p.mrn())).containsExactly(p.mrn());
        assertThat(mrns(p.mrn().toLowerCase())).containsExactly(p.mrn());
        assertThat(mrns("CC" + number)).containsExactly(p.mrn());
        if (number.length() == 6) {
            assertThat(mrns(number)).containsExactly(p.mrn());
        }
    }

    @Test
    void findsByDateOfBirthInBothWritings() {
        LocalDate dob = LocalDate.of(1931, 2, 3);
        PatientView p = register(unique(), unique(), dob, null);

        assertThat(mrns("1931-02-03")).contains(p.mrn());
        assertThat(mrns("03/02/1931")).contains(p.mrn());
        assertThat(mrns("3.2.1931")).contains(p.mrn());
        assertThat(mrns("02/03/1931")).doesNotContain(p.mrn()); // day first: this is 2 March
    }

    @Test
    void findsByPhoneWhateverTheFormattingOrCountryCode() {
        String phone = String.format("9%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        PatientView p = register(unique(), unique(), LocalDate.of(1990, 1, 1), phone.substring(0, 5) + " " + phone.substring(5));

        assertThat(mrns(phone)).containsExactly(p.mrn());
        assertThat(mrns("+91 " + phone.substring(0, 5) + "-" + phone.substring(5))).isEmpty(); // other digits in front
        assertThat(mrns(phone.substring(2))).containsExactly(p.mrn());
    }

    @Test
    void nothingFoundIsAnEmptyPageNotAnError() {
        var page = search.search(unique(), 0, 20);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void anEmptySearchAndWildcardsListNobody() {
        register(unique(), unique(), LocalDate.of(1990, 1, 1), null);

        assertThatThrownBy(() -> search.search("   ", 0, 20))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(mrns("%")).isEmpty();
        assertThat(mrns("_")).isEmpty();
        assertThat(mrns("Zq%")).isEmpty();
        assertThat(mrns("\\")).isEmpty();
    }

    @Test
    void resultsAreSortedAndPaged() {
        String last = unique();
        PatientView b = register("Bela", last, LocalDate.of(1990, 1, 1), null);
        PatientView a = register("Anu", last, LocalDate.of(1990, 1, 2), null);
        PatientView c = register("Chitra", last, LocalDate.of(1990, 1, 3), null);

        var first = search.search(last, 0, 2);
        var second = search.search(last, 1, 2);

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).extracting(PatientSummary::mrn).containsExactly(a.mrn(), b.mrn());
        assertThat(second.getContent()).extracting(PatientSummary::mrn).containsExactly(c.mrn());
        // The page size is capped.
        assertThat(search.search(last, 0, 100_000).getSize()).isEqualTo(PatientSearchService.MAX_PAGE_SIZE);
    }
}
