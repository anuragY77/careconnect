package com.careconnect.auth.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Makes the one-time password an administrator hands to a staff user. Characters that look alike
 * (0/O, 1/l/I) are left out so it can be read from paper without mistakes.
 */
@Component
public class TemporaryPasswords {

    static final int LENGTH = 16;

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;

    private final SecureRandom random = new SecureRandom();

    /** Always contains at least one letter and one digit, so it passes {@link PasswordPolicy}. */
    public String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = pick(LETTERS);
        chars[1] = pick(DIGITS);
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        // Shuffle (Fisher-Yates) so the letter and the digit are not always first.
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
