package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.careconnect.common.error.ErrorCode;
import com.careconnect.common.error.TooManyRequestsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-02-01T08:00:00Z");

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

    private TestClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        LoginProperties properties = new LoginProperties();
        properties.setIpMaxFailures(3);
        properties.setIpWindowMinutes(15);
        clock = new TestClock();
        throttle = new LoginThrottle(properties, clock);
    }

    @Test
    void allowsAttemptsBelowTheLimit() {
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");

        assertThatCode(() -> throttle.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void blocksAfterTheLimitWithARetryTime() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> throttle.checkAllowed("10.0.0.1"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                assertThat(ex.getRetryAfterSeconds()).isEqualTo(600);
                assertThat(ex.getMessage()).doesNotContain("10.0.0.1");
            });
    }

    @Test
    void otherAddressesAreNotAffected() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("10.0.0.1");
        }

        assertThatCode(() -> throttle.checkAllowed("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void allowsAgainAfterTheWindow() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> throttle.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void missingAddressIsTreatedAsOneSharedKeyNotIgnored() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(null);
        }

        assertThatThrownBy(() -> throttle.checkAllowed(" ")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void retryAfterIsNeverBelowOneSecond() {
        assertThat(new TooManyRequestsException("x", 0).getRetryAfterSeconds()).isEqualTo(1);
    }
}
