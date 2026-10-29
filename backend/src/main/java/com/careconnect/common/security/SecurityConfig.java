package com.careconnect.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Temporary deny-by-default configuration. Only the health probe and the three auth endpoints
 * (login, refresh, logout) are public. Replaced by the real JWT-based configuration in TASK-017.
 *
 * <p>CSRF protection is off because no endpoint relies on a cookie for authorisation except the
 * refresh and logout endpoints, and those are protected by SameSite=Strict and the Origin check.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                    .permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
