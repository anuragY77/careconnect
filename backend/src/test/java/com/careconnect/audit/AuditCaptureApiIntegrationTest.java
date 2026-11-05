package com.careconnect.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Audit capture through the real HTTP chain: who did it and from where (TC-72 to TC-74 groundwork). */
// Same settings as the other API integration tests, so they share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditCaptureApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private AppUser newUser(Role role) {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name,
            name + "@example.org", role, null, false));
    }

    private String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void aFailedLoginOverHttpIsAuditedWithTheClientAddress() throws Exception {
        AppUser user = newUser(Role.DOCTOR);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(user.getUsername(), "wrong-password-1")))
            .andExpect(status().isUnauthorized());

        List<Map<String, Object>> rows = jdbc.queryForList(
            "select * from audit_event where username = ?", user.getUsername());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "LOGIN").containsEntry("outcome", "FAILURE")
            .containsEntry("client_ip", "127.0.0.1");
    }

    @Test
    void anAdminActionIsAuditedWithTheAdminFromTheAccessToken() throws Exception {
        AppUser admin = newUser(Role.ADMIN);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(admin.getUsername(), PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String name = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String created = mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"fullName\":\"Nurse New\",\"email\":\"" + name
                    + "@example.org\",\"role\":\"NURSE\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer newId = JsonPath.read(created, "$.account.userId");

        Map<String, Object> row = jdbc.queryForMap(
            "select * from audit_event where action = 'STAFF_CREATED' and resource_id = ?", String.valueOf(newId));
        assertThat(row).containsEntry("user_id", admin.getId()).containsEntry("role", "ADMIN")
            .containsEntry("outcome", "SUCCESS").containsEntry("client_ip", "127.0.0.1");
    }
}
