package com.careconnect.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues short-lived access tokens. A token holds only what the server needs to authorise a
 * request: user id (subject), role, issuer, times and a unique id. It holds no name, e-mail or
 * other personal data, because anyone who has the token can read it.
 */
@Component
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    /** A signed token and the number of seconds it stays valid. */
    public record AccessToken(String value, long expiresInSeconds) {

        /** Keeps the token out of logs. */
        @Override
        public String toString() {
            return "AccessToken[expiresInSeconds=" + expiresInSeconds + "]";
        }
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.getIssuer())
            .subject(Long.toString(user.userId()))
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTtl()))
            .id(UUID.randomUUID().toString())
            .claim(ROLE_CLAIM, user.role().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTtl().toSeconds());
    }
}
