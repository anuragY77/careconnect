package com.careconnect.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.audit.domain.AuditEvent;
import com.careconnect.audit.repository.AuditStore;
import com.careconnect.audit.repository.PostgresAuditStore;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fail-closed behaviour: when the audit store cannot write, the request fails and the business
 * change is rolled back with it. Nothing is done without a record (NFR-08, ARCHITECTURE 3.6).
 */
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuditFailClosedIntegrationTest.BrokenAuditConfig.class})
class AuditFailClosedIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";
    static final AtomicBoolean BROKEN = new AtomicBoolean(false);

    /** Replaces the audit store with one that fails on demand and otherwise writes for real. */
    @TestConfiguration
    static class BrokenAuditConfig {
        @Bean
        @Primary
        AuditStore switchableAuditStore(PostgresAuditStore real) {
            return (AuditEvent event) -> {
                if (BROKEN.get()) {
                    throw new IllegalStateException("audit store is down (test)");
                }
                real.append(event);
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void repairStore() {
        BROKEN.set(false);
    }

    private AppUser newUser(Role role) {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name,
            name + "@example.org", role, null, false));
    }

    private String loginJson(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void loginFailsAndGivesNoTokenWhenTheAuditWriteFails() throws Exception {
        AppUser user = newUser(Role.DOCTOR);
        BROKEN.set(true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(user.getUsername())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("accessToken"))));

        // The success was not recorded, so it did not happen: no last-login time either.
        assertThat(jdbc.queryForObject("select last_login_at from app_user where user_id = ?",
            java.sql.Timestamp.class, user.getId())).isNull();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?",
            Long.class, user.getId())).isZero();
    }

    @Test
    void anAdminChangeIsRolledBackWhenTheAuditWriteFails() throws Exception {
        AppUser admin = newUser(Role.ADMIN);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(admin.getUsername())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String name = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        BROKEN.set(true);

        mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"fullName\":\"Nurse New\",\"email\":\"" + name
                    + "@example.org\",\"role\":\"NURSE\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("temporaryPassword"))));

        assertThat(users.existsByUsernameIgnoreCase(name)).isFalse();
    }

    @Test
    void everythingWorksAgainOnceTheStoreIsBack() throws Exception {
        AppUser user = newUser(Role.DOCTOR);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(user.getUsername())))
            .andExpect(status().isOk());
    }
}
