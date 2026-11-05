package com.careconnect.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/** The admin staff-account API and the forced password change, through the full security chain. */
// Same settings as AuthTokenEndpointsIntegrationTest, so both share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffAccountApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";
    private static final String BASE = "/api/admin/staff-accounts";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private AppUser newUser(Role role) {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name,
            name + "@example.org", role, null, false));
    }

    private String loginToken(String username, String password) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void onlyAnAdministratorCanManageStaff() throws Exception {
        String doctor = loginToken(newUser(Role.DOCTOR).getUsername(), PASSWORD);

        mvc.perform(get(BASE).header("Authorization", bearer(doctor))).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void newStaffMustChangeTheTemporaryPasswordBeforeDoingAnythingElse() throws Exception {
        String admin = loginToken(newUser(Role.ADMIN).getUsername(), PASSWORD);
        String name = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String created = mvc.perform(post(BASE).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"fullName\":\"Nurse New\",\"email\":\"" + name
                    + "@example.org\",\"role\":\"NURSE\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.account.mustChangePassword").value(true))
            .andExpect(jsonPath("$.account.role").value("NURSE"))
            .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(created, "$.temporaryPassword");
        assertThat(created).doesNotContain("passwordHash");

        // The token of a user with a temporary password carries no real role ...
        String limited = loginToken(name, temporary);
        assertThat(jwtDecoder.decode(limited).getClaimAsString("role")).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(limited)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get(BASE).header("Authorization", bearer(limited))).andExpect(status().isForbidden());

        // ... until the password is changed. Then the real role is back.
        String change = "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"Brand-New-Pass-77\"}";
        String changed = mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(limited))
                .contentType(MediaType.APPLICATION_JSON).content(change))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Set-Cookie", containsString("cc_refresh=")))
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(changed, "$.accessToken");
        assertThat(jwtDecoder.decode(token).getClaimAsString("role")).isEqualTo("NURSE");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"password\":\"" + temporary + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRefusesAWrongCurrentPasswordAndAWeakNewOne() throws Exception {
        AppUser user = newUser(Role.DOCTOR);
        String token = loginToken(user.getUsername(), PASSWORD);

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"not-the-password-1\",\"newPassword\":\"Brand-New-Pass-77\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short1\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createRejectsABadRoleAndAnInvalidUsername() throws Exception {
        String admin = loginToken(newUser(Role.ADMIN).getUsername(), PASSWORD);

        mvc.perform(post(BASE).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ok.name\",\"fullName\":\"X\",\"email\":\"x@example.org\",\"role\":\"PATIENT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(BASE).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bad name!\",\"fullName\":\"X\",\"email\":\"x@example.org\",\"role\":\"NURSE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
