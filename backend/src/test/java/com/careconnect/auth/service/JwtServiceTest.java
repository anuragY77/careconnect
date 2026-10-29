package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.SecretKey;

import com.careconnect.auth.domain.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz";

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-03-01T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final AuthenticatedUser doctor =
        new AuthenticatedUser(42L, "dr.rao", "Dr Rao", Role.DOCTOR, false);

    private TestClock clock;
    private JwtProperties properties;
    private JwtConfig config;
    private SecretKey key;
    private JwtService service;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        config = new JwtConfig();
        key = JwtSigningKey.from(SECRET, false);
        service = new JwtService(config.jwtEncoder(key), properties, clock);
        decoder = config.jwtDecoder(key, properties, clock);
    }

    @Test
    void tokenCarriesOnlyTheExpectedClaims() {
        Jwt jwt = decoder.decode(service.issue(doctor).value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("careconnect");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("DOCTOR");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-03-01T09:15:00Z"));
        // No personal data in the token: only these claims exist.
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti", "role");
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
    }

    @Test
    void reportsTheLifetimeInSeconds() {
        assertThat(service.issue(doctor).expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void everyTokenHasItsOwnId() {
        String first = decoder.decode(service.issue(doctor).value()).getId();
        String second = decoder.decode(service.issue(doctor).value()).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tokenIsValidUntilItsExpiryAndNotAfter() {
        String token = service.issue(doctor).value();

        clock.advance(Duration.ofMinutes(15));
        assertThat(decoder.decode(token)).isNotNull();

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecretKey other = JwtSigningKey.from("another-secret-0123456789-abcdefghijklmnopqrstuvwxyz", false);
        JwtService foreign = new JwtService(config.jwtEncoder(other), properties, clock);

        assertThatThrownBy(() -> decoder.decode(foreign.issue(doctor).value())).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithAnotherIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties();
        otherIssuer.setSecret(SECRET);
        otherIssuer.setIssuer("someone-else");
        JwtService foreign = new JwtService(config.jwtEncoder(key), otherIssuer, clock);

        assertThatThrownBy(() -> decoder.decode(foreign.issue(doctor).value())).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = service.issue(doctor).value().split("\\.");
        String payload = parts[1];
        char flipped = payload.charAt(5) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + payload.substring(0, 5) + flipped + payload.substring(6) + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
            "{\"sub\":\"1\",\"iss\":\"careconnect\",\"role\":\"ADMIN\",\"exp\":4102444800}"
                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decoder.decode(header + "." + payload + ".")).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> decoder.decode("not-a-token")).isInstanceOf(JwtException.class);
    }
}
