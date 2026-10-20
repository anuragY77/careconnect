package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsAStrongPassword() {
        assertThat(policy.isValid("Correct-Horse-42", "dr.smith")).isTrue();
    }

    @Test
    void rejectsMissingPassword() {
        assertThat(policy.violations(null, "u")).containsExactly("Password is required");
        assertThat(policy.violations("", "u")).containsExactly("Password is required");
    }

    @Test
    void rejectsShortPassword() {
        assertThat(policy.violations("Ab1", "u")).anyMatch(m -> m.contains("at least 12"));
    }

    @Test
    void exactlyTwelveCharactersIsEnough() {
        assertThat(policy.isValid("Abcdefghij12", "u")).isTrue();
        assertThat(policy.isValid("Abcdefghi12", "u")).isFalse();
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertThat(policy.violations("OnlyLettersHere", "u")).containsExactly("Password must contain a digit");
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertThat(policy.violations("123456789012", "u")).containsExactly("Password must contain a letter");
    }

    @Test
    void rejectsPasswordContainingTheUsernameIgnoringCase() {
        assertThat(policy.violations("My-DR.SMITH-pass1", "dr.smith"))
            .containsExactly("Password must not contain the username");
    }

    @Test
    void rejectsPasswordLongerThan72Bytes() {
        String tooLong = "a1".repeat(37); // 74 bytes
        assertThat(policy.violations(tooLong, "u")).anyMatch(m -> m.contains("at most 72"));
        assertThat(policy.isValid("a1".repeat(36), "u")).isTrue(); // exactly 72 bytes
    }

    @Test
    void countsBytesNotCharacters() {
        // 36 two-byte characters + "ab1" = 75 bytes but only 39 characters
        String multibyte = "é".repeat(36) + "ab1";
        assertThat(policy.violations(multibyte, "u")).anyMatch(m -> m.contains("at most 72"));
    }
}
