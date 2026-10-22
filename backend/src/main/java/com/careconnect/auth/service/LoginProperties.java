package com.careconnect.auth.service;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Login protection settings, {@code careconnect.login.*}: per-account lockout
 * (LOGIN_MAX_FAILED_ATTEMPTS, LOGIN_LOCK_MINUTES) and per-IP failure limit
 * (LOGIN_IP_MAX_FAILURES, LOGIN_IP_WINDOW_MINUTES).
 */
@Component
@ConfigurationProperties(prefix = "careconnect.login")
@Validated
public class LoginProperties {

    @Min(1)
    private int maxFailedAttempts = 5;

    @Min(1)
    private long lockMinutes = 15;

    @Min(1)
    private int ipMaxFailures = 20;

    @Min(1)
    private long ipWindowMinutes = 15;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(long lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getIpMaxFailures() {
        return ipMaxFailures;
    }

    public void setIpMaxFailures(int ipMaxFailures) {
        this.ipMaxFailures = ipMaxFailures;
    }

    public long getIpWindowMinutes() {
        return ipWindowMinutes;
    }

    public void setIpWindowMinutes(long ipWindowMinutes) {
        this.ipWindowMinutes = ipWindowMinutes;
    }

    public Duration ipWindow() {
        return Duration.ofMinutes(ipWindowMinutes);
    }

    public Duration lockDuration() {
        return Duration.ofMinutes(lockMinutes);
    }
}
