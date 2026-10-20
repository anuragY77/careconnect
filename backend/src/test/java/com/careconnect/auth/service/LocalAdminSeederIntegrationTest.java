package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Starts the whole application with the local profile and checks the seeded admin in the real database. */
@SpringBootTest(properties = "careconnect.seed.admin.password=Seed-Local-2026x")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class LocalAdminSeederIntegrationTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void seedsOneAdminAtStartup() {
        AppUser admin = users.findByUsernameIgnoreCase("admin").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$12$");
        assertThat(encoder.matches("Seed-Local-2026x", admin.getPasswordHash())).isTrue();
        assertThat(users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN)).hasSize(1);
    }
}
