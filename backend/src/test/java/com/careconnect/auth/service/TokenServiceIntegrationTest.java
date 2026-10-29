package com.careconnect.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Refresh token rules against a real PostgreSQL: hashing, rotation, reuse detection (also for
 * two requests at the same moment), idle and absolute expiry, deactivated users, logout and purge.
 * A movable clock replaces waiting days. Each test creates its own user and removes its tokens
 * and the user afterwards.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TokenServiceIntegrationTest.MovableClockConfig.class})
class TokenServiceIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    /** A clock the test can move forward. */
    static class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-03-01T09:00:00Z");

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

    @TestConfiguration(proxyBeanMethods = false)
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MovableClock clock;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Tokens reference users, so they go first.
        createdIds.forEach(id -> jdbc.update("delete from refresh_token where user_id = ?", id));
        createdIds.forEach(users::deleteById);
    }

    private AuthenticatedUser newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser saved = users.save(AppUser.create(name, encoder.encode(PASSWORD), "Test " + name,
            name + "@example.org", Role.NURSE, null, false));
        createdIds.add(saved.getId());
        return new AuthenticatedUser(saved.getId(), saved.getUsername(), saved.getFullName(), Role.NURSE, false);
    }

    private int rows(long userId) {
        Integer count = jdbc.queryForObject("select count(*) from refresh_token where user_id = ?",
            Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int notRevoked(long userId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, userId);
        return count == null ? 0 : count;
    }

    @Test
    void startSessionStoresOnlyTheHashOfTheToken() {
        AuthenticatedUser user = newUser();

        IssuedSession session = tokenService.startSession(user);

        assertThat(session.refreshToken()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(session.accessToken().value()).isNotBlank();
        assertThat(session.refreshExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        String stored = jdbc.queryForObject("select token_hash from refresh_token where user_id = ?",
            String.class, user.userId());
        assertThat(stored).isEqualTo(TokenService.hash(session.refreshToken()));
        assertThat(stored).isNotEqualTo(session.refreshToken()).hasSize(64);
    }

    @Test
    void everySessionGetsADifferentToken() {
        AuthenticatedUser user = newUser();

        assertThat(tokenService.startSession(user).refreshToken())
            .isNotEqualTo(tokenService.startSession(user).refreshToken());
    }

    @Test
    void rotateGivesANewTokenAndMarksTheOldOneUsed() {
        AuthenticatedUser user = newUser();
        IssuedSession first = tokenService.startSession(user);

        Optional<IssuedSession> second = tokenService.rotate(first.refreshToken());

        assertThat(second).isPresent();
        assertThat(second.get().refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.get().user().userId()).isEqualTo(user.userId());
        assertThat(second.get().accessToken().value()).isNotBlank();
        assertThat(rows(user.userId())).isEqualTo(2);
        Integer used = jdbc.queryForObject(
            "select count(*) from refresh_token where user_id = ? and used_at is not null", Integer.class,
            user.userId());
        assertThat(used).isEqualTo(1);
        // The new token works too: the chain continues.
        assertThat(tokenService.rotate(second.get().refreshToken())).isPresent();
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() {
        AuthenticatedUser user = newUser();
        IssuedSession first = tokenService.startSession(user);
        IssuedSession second = tokenService.rotate(first.refreshToken()).orElseThrow();

        assertThat(tokenService.rotate(first.refreshToken())).isEmpty();

        // The thief's copy and the owner's newest token are both dead now.
        assertThat(tokenService.rotate(second.refreshToken())).isEmpty();
        assertThat(notRevoked(user.userId())).isZero();
    }

    @Test
    void reuseOfOneSessionDoesNotTouchAnotherSessionOfTheSameUser() {
        AuthenticatedUser user = newUser();
        IssuedSession laptop = tokenService.startSession(user);
        IssuedSession phone = tokenService.startSession(user);
        tokenService.rotate(laptop.refreshToken()).orElseThrow();

        assertThat(tokenService.rotate(laptop.refreshToken())).isEmpty();

        assertThat(tokenService.rotate(phone.refreshToken())).isPresent();
    }

    @Test
    void twoRequestsWithTheSameTokenAtTheSameTimeNeverBothSucceed() throws Exception {
        AuthenticatedUser user = newUser();
        IssuedSession first = tokenService.startSession(user);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Optional<IssuedSession>>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return tokenService.rotate(first.refreshToken());
                }));
            }
            go.countDown();
            int successes = 0;
            for (Future<Optional<IssuedSession>> result : results) {
                if (result.get().isPresent()) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);
            // The second request looked like a reuse, so the session is revoked.
            assertThat(notRevoked(user.userId())).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unknownOrMalformedTokensAreRejected() {
        assertThat(tokenService.rotate(null)).isEmpty();
        assertThat(tokenService.rotate("")).isEmpty();
        assertThat(tokenService.rotate("   ")).isEmpty();
        assertThat(tokenService.rotate("x".repeat(43))).isEmpty();
        assertThat(tokenService.rotate("x".repeat(TokenService.MAX_TOKEN_LENGTH + 1))).isEmpty();
    }

    @Test
    void tokenExpiresAfterTheIdleTime() {
        AuthenticatedUser user = newUser();
        IssuedSession session = tokenService.startSession(user);

        clock.advance(Duration.ofDays(7));

        assertThat(tokenService.rotate(session.refreshToken())).isEmpty();
    }

    @Test
    void tokenStillWorksJustBeforeTheIdleTimeEnds() {
        AuthenticatedUser user = newUser();
        IssuedSession session = tokenService.startSession(user);

        clock.advance(Duration.ofDays(7).minusSeconds(1));

        assertThat(tokenService.rotate(session.refreshToken())).isPresent();
    }

    @Test
    void renewingExtendsTheIdleTimeButNeverPastTheAbsoluteLimit() {
        AuthenticatedUser user = newUser();
        Instant begin = clock.instant();
        IssuedSession session = tokenService.startSession(user);
        Instant familyEnd = begin.plus(Duration.ofDays(30));

        // Renewed every 6 days: each token lives 7 days, until the 30-day limit cuts it short.
        for (int day = 6; day <= 24; day += 6) {
            clock.advance(Duration.ofDays(6));
            session = tokenService.rotate(session.refreshToken()).orElseThrow();
            Instant expected = clock.instant().plus(Duration.ofDays(7));
            assertThat(session.refreshExpiresAt()).isEqualTo(expected.isAfter(familyEnd) ? familyEnd : expected);
        }
        assertThat(session.refreshExpiresAt()).isEqualTo(familyEnd);

        clock.advance(Duration.ofDays(6));

        assertThat(clock.instant()).isEqualTo(familyEnd);
        assertThat(tokenService.rotate(session.refreshToken())).isEmpty();
    }

    @Test
    void deactivatedUserCannotRefreshAndTheSessionIsRevoked() {
        AuthenticatedUser user = newUser();
        IssuedSession session = tokenService.startSession(user);
        jdbc.update("update app_user set status = 'INACTIVE' where user_id = ?", user.userId());

        assertThat(tokenService.rotate(session.refreshToken())).isEmpty();

        assertThat(notRevoked(user.userId())).isZero();
    }

    @Test
    void refreshUsesTheCurrentRoleOfTheUser() {
        AuthenticatedUser user = newUser();
        IssuedSession session = tokenService.startSession(user);
        jdbc.update("update app_user set role = 'PHARMACIST' where user_id = ?", user.userId());

        IssuedSession renewed = tokenService.rotate(session.refreshToken()).orElseThrow();

        assertThat(renewed.user().role()).isEqualTo(Role.PHARMACIST);
    }

    @Test
    void endSessionRevokesTheWholeSession() {
        AuthenticatedUser user = newUser();
        IssuedSession first = tokenService.startSession(user);
        IssuedSession second = tokenService.rotate(first.refreshToken()).orElseThrow();

        tokenService.endSession(second.refreshToken());

        assertThat(tokenService.rotate(second.refreshToken())).isEmpty();
        assertThat(notRevoked(user.userId())).isZero();
    }

    @Test
    void endSessionIgnoresUnknownOrMissingTokens() {
        AuthenticatedUser user = newUser();
        IssuedSession session = tokenService.startSession(user);

        tokenService.endSession(null);
        tokenService.endSession("");
        tokenService.endSession("y".repeat(43));

        assertThat(tokenService.rotate(session.refreshToken())).isPresent();
    }

    @Test
    void purgeDeletesOnlyTokensExpiredMoreThanADayAgo() {
        AuthenticatedUser user = newUser();
        tokenService.startSession(user);
        assertThat(tokenService.purgeExpired()).isZero();
        assertThat(rows(user.userId())).isEqualTo(1);

        // Expired (day 7) but still inside the one-day grace period.
        clock.advance(Duration.ofDays(7).plusHours(12));
        tokenService.purgeExpired();
        assertThat(rows(user.userId())).isEqualTo(1);

        clock.advance(Duration.ofDays(1));
        tokenService.purgeExpired();
        assertThat(rows(user.userId())).isZero();
    }
}
