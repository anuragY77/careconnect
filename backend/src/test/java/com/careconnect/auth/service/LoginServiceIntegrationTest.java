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
 * TC-01 (login) and TC-02 (lockout) against a real PostgreSQL. The service commits its own
 * transaction, so these tests are not wrapped in one; each test creates users with unique
 * names and removes them afterwards. A movable clock replaces waiting 15 minutes.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, LoginServiceIntegrationTest.MovableClockConfig.class})
class LoginServiceIntegrationTest {

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
    private LoginService loginService;

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
        createdIds.forEach(users::deleteById);
    }

    private String newUser() {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser saved = users.save(AppUser.create(name, encoder.encode(PASSWORD), "Test " + name,
            name + "@example.org", Role.DOCTOR, null, true));
        createdIds.add(saved.getId());
        return name;
    }

    private AppUser reload(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow();
    }

    private void failTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(loginService.login(username, "Wrong-Password-1")).isEmpty();
        }
    }

    @Test
    void correctCredentialsLogIn() {
        String name = newUser();

        Optional<AuthenticatedUser> result = loginService.login(name, PASSWORD);

        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo(name);
        assertThat(result.get().role()).isEqualTo(Role.DOCTOR);
        assertThat(result.get().mustChangePassword()).isTrue();
        assertThat(reload(name).getLastLoginAt()).isEqualTo(clock.instant());
    }

    @Test
    void usernameIsMatchedIgnoringCaseAndSurroundingSpaces() {
        String name = newUser();

        assertThat(loginService.login("  " + name.toUpperCase() + " ", PASSWORD)).isPresent();
    }

    @Test
    void wrongPasswordFailsAndTheFailureIsStored() {
        String name = newUser();

        failTimes(name, 1);

        assertThat(reload(name).getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void unknownUserFails() {
        assertThat(loginService.login("nobody-" + UUID.randomUUID(), PASSWORD)).isEmpty();
    }

    @Test
    void passwordLongerThan72BytesFailsWithoutAnError() {
        String name = newUser();

        assertThat(loginService.login(name, "a1".repeat(40))).isEmpty();
        assertThat(reload(name).getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void nullInputFailsWithoutAnError() {
        assertThat(loginService.login(null, null)).isEmpty();
        String name = newUser();
        assertThat(loginService.login(name, null)).isEmpty();
    }

    @Test
    void fiveFailuresLockTheAccountAndEvenTheCorrectPasswordIsRefused() {
        String name = newUser();

        failTimes(name, 5);

        assertThat(reload(name).getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(loginService.login(name, PASSWORD)).isEmpty();
    }

    @Test
    void fourFailuresDoNotLock() {
        String name = newUser();

        failTimes(name, 4);

        assertThat(reload(name).getLockedUntil()).isNull();
        assertThat(loginService.login(name, PASSWORD)).isPresent();
    }

    @Test
    void attemptsDuringALockDoNotExtendIt() {
        String name = newUser();
        failTimes(name, 5);
        Instant lockEnd = reload(name).getLockedUntil();

        clock.advance(Duration.ofMinutes(5));
        failTimes(name, 3);
        loginService.login(name, PASSWORD);

        assertThat(reload(name).getLockedUntil()).isEqualTo(lockEnd);
    }

    @Test
    void loginWorksAgainAfterTheLockHasEnded() {
        String name = newUser();
        failTimes(name, 5);

        clock.advance(Duration.ofMinutes(15));

        assertThat(loginService.login(name, PASSWORD)).isPresent();
        AppUser after = reload(name);
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void aWrongPasswordAfterTheLockEndedStartsANewCount() {
        String name = newUser();
        failTimes(name, 5);

        clock.advance(Duration.ofMinutes(16));
        failTimes(name, 1);

        AppUser after = reload(name);
        assertThat(after.getFailedAttempts()).isEqualTo(1);
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginClearsEarlierFailures() {
        String name = newUser();
        failTimes(name, 3);

        assertThat(loginService.login(name, PASSWORD)).isPresent();

        assertThat(reload(name).getFailedAttempts()).isZero();
        failTimes(name, 4);
        assertThat(reload(name).getLockedUntil()).isNull();
    }

    @Test
    void lockingOneUserDoesNotAffectAnother() {
        String locked = newUser();
        String other = newUser();

        failTimes(locked, 5);

        assertThat(loginService.login(other, PASSWORD)).isPresent();
    }

    @Test
    void inactiveUserCannotLogInEvenWithTheCorrectPassword() {
        String name = newUser();
        jdbc.update("update app_user set status = 'INACTIVE' where lower(username) = lower(?)", name);

        assertThat(loginService.login(name, PASSWORD)).isEmpty();
        assertThat(reload(name).getFailedAttempts()).isZero();
    }
}
