package com.careconnect.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The login endpoint as a client sees it: status, body, problem format, and no information leaks. */
// The per-IP limit is raised so these tests (all from one address) never hit it; it has its own test class.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private String newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser saved = users.save(AppUser.create(name, encoder.encode(PASSWORD), "Dr " + name,
            name + "@example.org", Role.DOCTOR, null, false));
        return saved.getUsername();
    }

    private ResultActions login(String username, String password) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validLoginReturnsTheUserWithoutAnySecret() throws Exception {
        String name = newUser();

        login(name, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value(name))
            .andExpect(jsonPath("$.user.role").value("DOCTOR"))
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andExpect(jsonPath("$.user.userId").isNumber())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(content().string(not(containsString("password"))))
            .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void failedLoginSetsNoCookie() throws Exception {
        String name = newUser();

        login(name, "Wrong-Password-1")
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void wrongPasswordIsAProblemJson401() throws Exception {
        String name = newUser();

        login(name, "Wrong-Password-1")
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void unknownUserAndWrongPasswordLookIdentical() throws Exception {
        String name = newUser();

        String wrongPassword = login(name, "Wrong-Password-1").andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody" + UUID.randomUUID().toString().substring(0, 8), "Wrong-Password-1")
            .andReturn().getResponse().getContentAsString();

        // Same status, code and message; only the timestamp may differ.
        assertThat(stripTimestamp(unknownUser))
            .isEqualTo(stripTimestamp(wrongPassword));
    }

    @Test
    void lockedAccountLooksLikeAnyOtherFailureEvenWithTheCorrectPassword() throws Exception {
        String name = newUser();
        for (int i = 0; i < 5; i++) {
            login(name, "Wrong-Password-1").andExpect(status().isUnauthorized());
        }

        String locked = login(name, PASSWORD).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String wrong = login("nobody" + UUID.randomUUID().toString().substring(0, 8), PASSWORD)
            .andReturn().getResponse().getContentAsString();

        assertThat(stripTimestamp(locked)).isEqualTo(stripTimestamp(wrong));
    }

    @Test
    void blankFieldsAreRejectedAsValidationErrors() throws Exception {
        login("", "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginDoesNotAcceptGet() throws Exception {
        mvc.perform(get("/api/auth/login")).andExpect(status().is4xxClientError());
    }

    @Test
    void otherApiPathsStayClosed() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().is4xxClientError());
    }

    private static String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\",?", "").replace(",}", "}");
    }
}
