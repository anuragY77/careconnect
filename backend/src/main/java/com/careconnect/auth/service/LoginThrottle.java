package com.careconnect.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.careconnect.common.error.TooManyRequestsException;
import com.careconnect.common.ratelimit.SlidingWindowCounter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Limits failed logins per client IP. The account lockout protects one account from a guesser;
 * this protects all accounts from one source that tries a few passwords on many accounts.
 *
 * <p>Only failures count, so many legitimate staff behind one hospital address are not slowed down.
 * A success never clears the failures: otherwise an attacker with one valid account could log in
 * between guesses and reset the counter.
 */
@Component
public class LoginThrottle {

    static final int MAX_TRACKED_IPS = 100_000;

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final SlidingWindowCounter failures;
    private final Clock clock;

    public LoginThrottle(LoginProperties properties, Clock clock) {
        this.failures = new SlidingWindowCounter(properties.ipWindow(), properties.getIpMaxFailures(),
            MAX_TRACKED_IPS);
        this.clock = clock;
    }

    /** Throws {@link TooManyRequestsException} when this IP used up its failed attempts. */
    public void checkAllowed(String clientIp) {
        Instant now = clock.instant();
        Optional<Duration> blocked = failures.blockedFor(key(clientIp), now);
        if (blocked.isPresent()) {
            long seconds = (blocked.get().toMillis() + 999) / 1000;
            log.warn("Login blocked for too many failed attempts from one address; retry in {}s", seconds);
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.", seconds);
        }
    }

    public void recordFailure(String clientIp) {
        failures.record(key(clientIp), clock.instant());
    }

    private static String key(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }
}
