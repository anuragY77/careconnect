package com.careconnect.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real security filter chain: which routes are public, how tokens are checked, what a failure
 * looks like, and the security headers. GET /api/auth/me is the probe for a protected endpoint.
 */
// Same settings as the other auth endpoint tests, so they share one application context and database.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityChainIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private AppUser newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return users.save(AppUser.create(name, encoder.encode(PASSWORD), "Dr " + name,
            name + "@example.org", Role.DOCTOR, null, false));
    }

    private MvcResult login(AppUser user) throws Exception {
        String json = "{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn();
    }

    private static String accessTokenOf(MvcResult login) throws Exception {
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions me(String bearer) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bearer));
    }

    private String tokenFor(long userId, Role role) {
        return jwtService.issue(new AuthenticatedUser(userId, "x", "X", role, false)).value();
    }

    private String signedWith(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt, String subject) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
            .issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString()).claim("role", "ADMIN").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    private void assertUnauthenticated(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void protectedEndpointWithoutTokenIs401Problem() throws Exception {
        assertUnauthenticated(mvc.perform(get("/api/auth/me")));
    }

    @Test
    void validTokenReachesTheProtectedEndpoint() throws Exception {
        AppUser user = newUser();
        String token = accessTokenOf(login(user));

        me(token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getId()))
            .andExpect(jsonPath("$.username").value(user.getUsername()))
            .andExpect(jsonPath("$.role").value("DOCTOR"))
            .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        assertUnauthenticated(me("not-a-token"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        AppUser user = newUser();
        Instant now = Instant.now();
        String expired = signedWith(jwtEncoder, "careconnect", now.minusSeconds(7200), now.minusSeconds(3600),
            user.getId().toString());

        assertUnauthenticated(me(expired));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        AppUser user = newUser();
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
            "some-other-secret-0123456789-abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = signedWith(foreign, "careconnect", now, now.plusSeconds(600), user.getId().toString());

        assertUnauthenticated(me(forged));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        AppUser user = newUser();
        Instant now = Instant.now();
        String token = signedWith(jwtEncoder, "someone-else", now, now.plusSeconds(600), user.getId().toString());

        assertUnauthenticated(me(token));
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"1\",\"iss\":\"careconnect\",\"role\":\"ADMIN\","
            + "\"exp\":4102444800}").getBytes(StandardCharsets.UTF_8));

        assertUnauthenticated(me(header + "." + payload + "."));
    }

    @Test
    void tokenInTheUrlIsNotAccepted() throws Exception {
        AppUser user = newUser();
        String token = accessTokenOf(login(user));

        assertUnauthenticated(mvc.perform(get("/api/auth/me").queryParam("access_token", token)));
    }

    @Test
    void deactivatedUserIsRefusedAtOnceOnMe() throws Exception {
        AppUser user = newUser();
        String token = accessTokenOf(login(user));
        jdbc.update("update app_user set status = 'INACTIVE' where user_id = ?", user.getId());

        assertUnauthenticated(me(token));
    }

    @Test
    void tokenForAUserThatDoesNotExistIsRefusedOnMe() throws Exception {
        assertUnauthenticated(me(tokenFor(987_654_321L, Role.DOCTOR)));
    }

    @Test
    void tokenWithANonNumericSubjectIsRefusedOnMe() throws Exception {
        Instant now = Instant.now();
        String token = signedWith(jwtEncoder, "careconnect", now, now.plusSeconds(600), "not-a-number");

        assertUnauthenticated(me(token));
    }

    @Test
    void publicAuthCallsIgnoreAnExpiredOrBrokenBearerHeader() throws Exception {
        AppUser user = newUser();
        MvcResult login = mvc.perform(post("/api/auth/login").header("Authorization", "Bearer garbage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("cc_refresh");
        assertThat(cookie).isNotNull();

        // The web app calls refresh exactly when its access token has expired.
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer expired-or-broken").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void otherPathsAreDeniedEvenWithAValidToken() throws Exception {
        AppUser user = newUser();
        String token = accessTokenOf(login(user));

        mvc.perform(get("/something-else").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void crossOriginPreflightIsNotAnswered() throws Exception {
        mvc.perform(options("/api/auth/me")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
