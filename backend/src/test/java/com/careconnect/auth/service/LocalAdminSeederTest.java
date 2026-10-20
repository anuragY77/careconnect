package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalAdminSeederTest {

    private static final String PASSWORD = "Local-Admin-2026x";

    private AppUserRepository users;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-value");
    }

    private LocalAdminSeeder seeder(String password) {
        return new LocalAdminSeeder(users, encoder, new PasswordPolicy(), password);
    }

    @Test
    void createsTheAdminWithAHashedPasswordAndForcedPasswordChange() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        seeder(PASSWORD).run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        AppUser admin = saved.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).isEqualTo("hashed-value").isNotEqualTo(PASSWORD);
        assertThat(admin.isMustChangePassword()).isTrue();
    }

    @Test
    void doesNothingWhenNoPasswordIsConfigured() {
        seeder("").run(null);
        seeder(null).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        seeder(PASSWORD).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void refusesToStartWithAWeakPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> seeder("short").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SEED_ADMIN_PASSWORD")
            .hasMessageContaining("at least 12");
        verify(users, never()).save(any());
    }
}
