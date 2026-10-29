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
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Login, refresh and logout as a browser sees them: body, cookie attributes, origin check, reuse. */
// Same settings as AuthControllerIntegrationTest, so both share one application context and one database.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthTokenEndpointsIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";
    private static final String COOKIE = "cc_refresh";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    private AppUser newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "Dr " + name,
            name + "@example.org", Role.DOCTOR, null, false));
    }

    private ResultActions login(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private ResultActions refresh(String cookieValue) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookieValue)));
    }

    private static String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\",?", "").replace(",}", "}");
    }

    @Test
    void loginReturnsAnAccessTokenAndSetsTheRefreshCookie() throws Exception {
        AppUser user = newUser();

        MvcResult result = login(user.getUsername())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Set-Cookie", containsString("cc_refresh=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Secure")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        // The refresh token is only in the cookie, never in the body.
        assertThat(body).doesNotContain(refreshCookieOf(result));
        assertThat(body).doesNotContain("refresh");
    }

    @Test
    void accessTokenIsAValidJwtForTheUser() throws Exception {
        AppUser user = newUser();

        String body = login(user.getUsername()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("DOCTOR");
    }

    @Test
    void refreshGivesNewTokensAndTheOldCookieStopsWorking() throws Exception {
        AppUser user = newUser();
        String first = refreshCookieOf(login(user.getUsername()).andReturn());

        MvcResult renewed = refresh(first)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value(user.getUsername()))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andReturn();
        String second = refreshCookieOf(renewed);
        assertThat(second).isNotEqualTo(first);

        // Using the first cookie again is reuse: it fails, clears the cookie and revokes the session.
        refresh(first)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.detail").value("Your session has ended. Sign in again."))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutOrWithABadCookieLooksTheSame() throws Exception {
        String missing = mvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String bad = refresh("x".repeat(43))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(stripTimestamp(missing)).isEqualTo(stripTimestamp(bad));
        assertThat(missing).contains("UNAUTHENTICATED");
    }

    @Test
    void refreshFromAForeignOriginIsRefusedAndDoesNotUseUpTheToken() throws Exception {
        AppUser user = newUser();
        String cookie = refreshCookieOf(login(user.getUsername()).andReturn());

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookie)).header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookie)).header("Origin", "http://localhost:8080"))
            .andExpect(status().isOk());
    }

    @Test
    void loginFromAForeignOriginIsRefusedWithoutACookie() throws Exception {
        AppUser user = newUser();
        String json = "{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}";

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json)
                .header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookie() throws Exception {
        AppUser user = newUser();
        String cookie = refreshCookieOf(login(user.getUsername()).andReturn());

        mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, cookie)))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutACookieIsHarmless() throws Exception {
        mvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void logoutFromAForeignOriginIsRefusedAndKeepsTheSession() throws Exception {
        AppUser user = newUser();
        String cookie = refreshCookieOf(login(user.getUsername()).andReturn());

        mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, cookie)).header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden());

        refresh(cookie).andExpect(status().isOk());
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        AppUser user = newUser();
        String cookie = refreshCookieOf(login(user.getUsername()).andReturn());
        // Deactivation is done directly here; the staff management endpoint comes later.
        jdbc.update("update app_user set status = 'INACTIVE' where user_id = ?", user.getId());

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshAndLogoutDoNotAcceptGet() throws Exception {
        mvc.perform(get("/api/auth/refresh")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/auth/logout")).andExpect(status().is4xxClientError());
    }

    @Test
    void refreshTokenIsNotAcceptedFromTheBodyOrAHeader() throws Exception {
        AppUser user = newUser();
        String cookie = refreshCookieOf(login(user.getUsername()).andReturn());

        mvc.perform(post("/api/auth/refresh").header("X-Refresh-Token", cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + cookie + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(not(containsString("accessToken"))));
    }
}
