package com.careconnect.patient.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

/**
 * What the single search box means. The kind is decided from the text: a date of birth, an MRN, a phone
 * number, or else a name. Parsing is separate from the database so the rules can be tested alone.
 */
sealed interface PatientSearchQuery {

    int MAX_LENGTH = 100;
    int MAX_NAME_WORDS = 5;
    /** Shorter digit strings are not a phone number (registration uses the same limit). */
    int MIN_PHONE_DIGITS = 7;

    Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    Pattern DAY_FIRST_DATE = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})");
    Pattern MRN = Pattern.compile("(?i)CC-?(\\d+)");
    /** An MRN number has six digits today; a phone number has at least seven. */
    Pattern MRN_DIGITS = Pattern.compile("\\d{6}");
    Pattern PHONE_CHARACTERS = Pattern.compile("[0-9+()\\- ]+");

    record ByDateOfBirth(LocalDate dateOfBirth) implements PatientSearchQuery { }

    record ByMrn(String mrn) implements PatientSearchQuery { }

    /** Digits only; matches the end of the stored number, so a missing country code still finds it. */
    record ByPhone(String digits) implements PatientSearchQuery { }

    /** Every word must start the first or the last name, in any order. Lower case. */
    record ByName(List<String> words) implements PatientSearchQuery { }

    static PatientSearchQuery parse(String raw) {
        String q = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (q.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "Enter a name, MRN, phone number or date of birth to search.");
        }
        if (q.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The search text is too long.");
        }
        LocalDate date = asDate(q);
        if (date != null) {
            return new ByDateOfBirth(date);
        }
        Matcher mrn = MRN.matcher(q);
        if (mrn.matches()) {
            return new ByMrn("CC-" + mrn.group(1));
        }
        if (MRN_DIGITS.matcher(q).matches()) {
            return new ByMrn("CC-" + q);
        }
        if (PHONE_CHARACTERS.matcher(q).matches()) {
            String digits = q.replaceAll("\\D", "");
            if (digits.length() >= MIN_PHONE_DIGITS) {
                return new ByPhone(digits);
            }
        }
        List<String> words = Arrays.stream(q.split(" ")).map(w -> w.toLowerCase(Locale.ROOT)).toList();
        if (words.size() > MAX_NAME_WORDS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Search with at most " + MAX_NAME_WORDS + " words.");
        }
        return new ByName(words);
    }

    /** 2000-01-31 or 31/01/2000 (day first, also with . or -). A text that is not a real date is not a date. */
    private static LocalDate asDate(String q) {
        try {
            Matcher iso = ISO_DATE.matcher(q);
            if (iso.matches()) {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
            }
            Matcher dayFirst = DAY_FIRST_DATE.matcher(q);
            if (dayFirst.matches()) {
                return LocalDate.of(Integer.parseInt(dayFirst.group(3)), Integer.parseInt(dayFirst.group(2)),
                    Integer.parseInt(dayFirst.group(1)));
            }
        } catch (DateTimeException notADate) {
            // for example 1234-56-78: may still be a phone number
        }
        return null;
    }
}
