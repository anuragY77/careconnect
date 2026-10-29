package com.careconnect.auth.service;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Token settings, {@code careconnect.jwt.*}: the signing secret (JWT_SECRET), the access token
 * lifetime (JWT_ACCESS_TTL_MINUTES), the idle lifetime of a refresh token (JWT_REFRESH_TTL_DAYS)
 * and the absolute lifetime of a whole sign-in (JWT_REFRESH_MAX_DAYS).
 */
@Component
@ConfigurationProperties(prefix = "careconnect.jwt")
@Validated
public class JwtProperties {

    /** Checked for length and for the example value in {@link JwtSigningKey}. */
    @NotBlank
    private String secret;

    @NotBlank
    private String issuer = "careconnect";

    @Min(1)
    @Max(60)
    private long accessTtlMinutes = 15;

    @Min(1)
    @Max(90)
    private long refreshTtlDays = 7;

    @Min(1)
    @Max(365)
    private long refreshMaxDays = 30;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }

    public long getRefreshMaxDays() {
        return refreshMaxDays;
    }

    public void setRefreshMaxDays(long refreshMaxDays) {
        this.refreshMaxDays = refreshMaxDays;
    }

    public Duration accessTtl() {
        return Duration.ofMinutes(accessTtlMinutes);
    }

    /** How long a refresh token lives without being used. */
    public Duration refreshIdleTtl() {
        return Duration.ofDays(refreshTtlDays);
    }

    /** How long one sign-in can last in total, however often it is refreshed. */
    public Duration refreshMaxLifetime() {
        return Duration.ofDays(refreshMaxDays);
    }
}
