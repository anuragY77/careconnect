package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    private final TemporaryPasswords generator = new TemporaryPasswords();
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void everyPasswordPassesThePolicyAndAvoidsLookAlikeCharacters() {
        for (int i = 0; i < 2000; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(TemporaryPasswords.LENGTH);
            assertThat(policy.isValid(password, "someone")).isTrue();
            assertThat(password).doesNotContain("0", "O", "1", "l", "I");
        }
    }

    @Test
    void passwordsDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(1000);
    }
}
