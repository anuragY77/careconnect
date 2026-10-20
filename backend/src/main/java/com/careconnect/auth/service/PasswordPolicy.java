package com.careconnect.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Password rules: at least 12 characters, at most 72 bytes (BCrypt ignores everything
 * after 72 bytes, so a longer password would give a false sense of security),
 * at least one letter and one digit, and not containing the username.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    /** Returns the broken rules as messages that are safe to show to the user. Empty means valid. */
    public List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("Password is required");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("Password must be at most " + MAX_BYTES + " bytes");
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            problems.add("Password must contain a letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Password must contain a digit");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.trim().toLowerCase(Locale.ROOT))) {
            problems.add("Password must not contain the username");
        }
        return problems;
    }

    public boolean isValid(String password, String username) {
        return violations(password, username).isEmpty();
    }
}
