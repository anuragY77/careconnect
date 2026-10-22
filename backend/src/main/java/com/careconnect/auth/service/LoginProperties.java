package com.careconnect.auth.service;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Lockout settings: {@code careconnect.login.*}, fed by LOGIN_MAX_FAILED_ATTEMPTS and LOGIN_LOCK_MINUTES. */
@Component
@ConfigurationProperties(prefix = "careconnect.login")
@Validated
public class LoginProperties {

    @Min(1)
    private int maxFailedAttempts = 5;

    @Min(1)
    private long lockMinutes = 15;

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

    public Duration lockDuration() {
        return Duration.ofMinutes(lockMinutes);
    }
}
