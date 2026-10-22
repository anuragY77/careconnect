package com.careconnect.auth.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

/**
 * The per-IP failed-login limit through the real application, with the limit set to 3.
 * Each test uses its own client address, because the counter lives for the whole application context.
 */
@SpringBootTest(properties = "careconnect.login.ip-max-failures=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginRateLimitIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    /** A different address (documentation range) every call, so tests never share a counter. */
    private static String newIp() {
        return "203.0.113." + NEXT_ADDRESS.getAndIncrement();
    }

    private String newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        users.save(AppUser.create(name, encoder.encode(PASSWORD), "Dr " + name, name + "@example.org",
            Role.DOCTOR, null, false));
        return name;
    }

    private ResultActions login(String ip, String username, String password) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            }));
    }

    private void failThreeTimes(String ip) throws Exception {
        for (int i = 0; i < 3; i++) {
            login(ip, "nobody" + i, "Wrong-Password-1").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void afterTheLimitTheAddressGets429WithRetryAfter() throws Exception {
        String ip = newIp();
        failThreeTimes(ip);

        login(ip, "nobody", "Wrong-Password-1")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aBlockedAddressIsRefusedEvenWithCorrectCredentials() throws Exception {
        String ip = newIp();
        String user = newUser();
        failThreeTimes(ip);

        login(ip, user, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherAddressesAreNotAffected() throws Exception {
        String blocked = newIp();
        String other = newIp();
        String user = newUser();
        failThreeTimes(blocked);

        login(other, user, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginsAreNeverCounted() throws Exception {
        String ip = newIp();
        String user = newUser();

        for (int i = 0; i < 10; i++) {
            login(ip, user, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void aSuccessDoesNotResetTheFailureCount() throws Exception {
        String ip = newIp();
        String user = newUser();

        login(ip, "nobody1", "Wrong-Password-1").andExpect(status().isUnauthorized());
        login(ip, "nobody2", "Wrong-Password-1").andExpect(status().isUnauthorized());
        login(ip, user, PASSWORD).andExpect(status().isOk());
        login(ip, "nobody3", "Wrong-Password-1").andExpect(status().isUnauthorized());

        login(ip, user, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidRequestsAreNotCounted() throws Exception {
        String ip = newIp();
        String user = newUser();

        for (int i = 0; i < 6; i++) {
            login(ip, "", "").andExpect(status().isBadRequest());
        }

        login(ip, user, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void sprayingManyDifferentAccountsFromOneAddressIsStopped() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 3; i++) {
            login(ip, newUser(), "Spray-Guess-123").andExpect(status().isUnauthorized());
        }

        login(ip, newUser(), "Spray-Guess-123").andExpect(status().isTooManyRequests());
    }
}
