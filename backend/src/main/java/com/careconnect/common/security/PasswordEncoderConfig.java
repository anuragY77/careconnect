package com.careconnect.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The single password hashing setting of the system: BCrypt with cost 12 (SECURITY.md). */
@Configuration
public class PasswordEncoderConfig {

    public static final int BCRYPT_COST = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }
}
