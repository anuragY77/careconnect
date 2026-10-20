package com.careconnect.auth.service;

import java.util.List;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin account for local development. It runs only in the
 * "local" profile, only when the password is configured, and only when no
 * admin exists yet. A weak password stops the startup instead of being accepted.
 * The password itself is never logged.
 */
@Component
@Profile("local")
public class LocalAdminSeeder implements ApplicationRunner {

    static final String USERNAME = "admin";
    static final String EMAIL = "admin@careconnect.local";

    private static final Logger log = LoggerFactory.getLogger(LocalAdminSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final String adminPassword;

    public LocalAdminSeeder(AppUserRepository users, PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy,
                            @Value("${careconnect.seed.admin.password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("SEED_ADMIN_PASSWORD is not set; no local admin account was created");
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        List<String> problems = passwordPolicy.violations(adminPassword, USERNAME);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD is not acceptable: " + String.join("; ", problems));
        }
        users.save(AppUser.create(USERNAME, passwordEncoder.encode(adminPassword),
            "Local Administrator", EMAIL, Role.ADMIN, null, true));
        log.info("Created local admin account '{}'; the password must be changed at first login", USERNAME);
    }
}
