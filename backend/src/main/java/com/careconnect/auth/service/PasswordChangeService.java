package com.careconnect.auth.service;

import java.time.Clock;
import java.util.List;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.repository.AppUserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a signed-in user choose a new password (also the forced change after a temporary one).
 * A wrong current password counts as a failed login attempt, so a stolen access token cannot be
 * used to guess the password without limit. The outcome is returned, not thrown, so that the
 * failure counter is committed.
 */
@Service
public class PasswordChangeService {

    public enum Outcome { CHANGED, WRONG_CURRENT_PASSWORD, NEW_PASSWORD_REJECTED, ACCOUNT_UNAVAILABLE }

    public record Result(Outcome outcome, List<String> problems, AuthenticatedUser user) {
        static Result of(Outcome outcome) {
            return new Result(outcome, List.of(), null);
        }
    }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginProperties loginProperties;
    private final TokenService tokenService;
    private final Clock clock;

    public PasswordChangeService(AppUserRepository users, PasswordEncoder passwordEncoder,
                                 PasswordPolicy passwordPolicy, LoginProperties loginProperties,
                                 TokenService tokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.loginProperties = loginProperties;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public Result change(long userId, String currentPassword, String newPassword) {
        AppUser user = users.findWithLockById(userId).filter(AppUser::isActive).orElse(null);
        if (user == null) {
            return Result.of(Outcome.ACCOUNT_UNAVAILABLE);
        }
        var now = clock.instant();
        if (user.isLockedAt(now)) {
            return Result.of(Outcome.WRONG_CURRENT_PASSWORD);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            user.recordFailedLogin(now, loginProperties.getMaxFailedAttempts(), loginProperties.lockDuration());
            return Result.of(Outcome.WRONG_CURRENT_PASSWORD);
        }
        List<String> problems = passwordPolicy.violations(newPassword, user.getUsername());
        if (newPassword.equals(currentPassword)) {
            problems = List.of("New password must be different from the current password");
        }
        if (!problems.isEmpty()) {
            return new Result(Outcome.NEW_PASSWORD_REJECTED, problems, null);
        }
        user.replacePassword(passwordEncoder.encode(newPassword), false);
        AuthenticatedUser result = AuthenticatedUser.from(user);
        tokenService.revokeAllSessions(userId); // last: it clears the persistence context
        return new Result(Outcome.CHANGED, List.of(), result);
    }
}
