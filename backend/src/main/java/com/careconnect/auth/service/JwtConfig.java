package com.careconnect.auth.service;

import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The JWT encoder (signs access tokens) and decoder (checks them). The decoder accepts only
 * HS256, our issuer and unexpired tokens, with no clock tolerance, measured on the application
 * {@link Clock}. The decoder is used by the security filter chain from TASK-017.
 */
@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties, Environment environment) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        return JwtSigningKey.from(properties.getSecret(), local);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            timestamps, new JwtIssuerValidator(properties.getIssuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
