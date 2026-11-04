package com.careconnect.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.JwtService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TC-04: the authorization matrix. Every role calls one probe endpoint per rule, and the answer
 * must be exactly what the rule says: 200 when allowed, 403 with code ACCESS_DENIED when not, and
 * 401 without a token. Each later feature adds its endpoints to a matrix like this one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationMatrixIntegrationTest {

    @RestController
    @RequestMapping("/api/test-authz")
    public static class AuthorizationProbeController {

        @GetMapping("/any")
        @AnyAuthenticatedUser
        public String any() {
            return "ok";
        }

        @GetMapping("/staff")
        @StaffOnly
        public String staff() {
            return "ok";
        }

        @GetMapping("/admin")
        @AdminOnly
        public String admin() {
            return "ok";
        }

        @GetMapping("/patient")
        @PatientOnly
        public String patient() {
            return "ok";
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        AuthorizationProbeController authorizationProbeController() {
            return new AuthorizationProbeController();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    private String tokenFor(Role role) {
        return jwtService.issue(new AuthenticatedUser(1L, "user", "User", role, false)).value();
    }

    /** A correctly signed token with the given role claim, or no role claim at all when {@code null}. */
    private String tokenWithRoleClaim(String role) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("careconnect").subject("1")
            .issuedAt(now).expiresAt(now.plusSeconds(600)).id(UUID.randomUUID().toString());
        if (role != null) {
            claims.claim("role", role);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            claims.build())).getTokenValue();
    }

    private void expect(String token, String path, boolean allowed) throws Exception {
        var request = get("/api/test-authz" + path).header("Authorization", "Bearer " + token);
        if (allowed) {
            mvc.perform(request).andExpect(status().isOk()).andExpect(content().string("ok"));
        } else {
            mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleGetsExactlyTheAnswerTheRulesSay(Role role) throws Exception {
        String token = tokenFor(role);

        expect(token, "/any", true);
        expect(token, "/staff", role.isStaff());
        expect(token, "/admin", role == Role.ADMIN);
        expect(token, "/patient", role == Role.PATIENT);
    }

    @Test
    void withoutATokenEveryProbeIs401() throws Exception {
        for (String path : new String[] {"/any", "/staff", "/admin", "/patient"}) {
            mvc.perform(get("/api/test-authz" + path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void tokenWithoutARoleClaimIsSignedInButHasNoRole() throws Exception {
        String token = tokenWithRoleClaim(null);

        expect(token, "/any", true);
        expect(token, "/staff", false);
        expect(token, "/admin", false);
        expect(token, "/patient", false);
    }

    @Test
    void unknownOrMalformedRoleGrantsNothing() throws Exception {
        for (String role : new String[] {"ROOT", "admin", "Admin", "ADMIN,DOCTOR", "ROLE_ADMIN", ""}) {
            String token = tokenWithRoleClaim(role);

            expect(token, "/any", true);
            expect(token, "/staff", false);
            expect(token, "/admin", false);
            expect(token, "/patient", false);
        }
    }
}
