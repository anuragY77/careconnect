package com.careconnect.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.audit.service.AuditEntry;
import com.careconnect.audit.service.AuditService;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a username and password and applies the lockout rules.
 *
 * <p>It returns an empty result for every kind of failure (unknown user, wrong password,
 * locked, inactive) and never throws for them. That matters twice: the caller cannot tell the
 * cases apart, and the failed-attempt counter is committed instead of being rolled back by an
 * exception. A password is always checked, even for an unknown user, so the response time does
 * not reveal whether the username exists.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginProperties properties;
    private final AuditService audit;
    private final Clock clock;
    /** A real hash that matches no password anyone can type; used to spend equal time for unknown users. */
    private final String unknownUserHash;

    public LoginService(AppUserRepository users, PasswordEncoder passwordEncoder,
                        LoginProperties properties, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.unknownUserHash = passwordEncoder.encode("not-a-real-password-" + System.nanoTime());
    }

    @Transactional
    public Optional<AuthenticatedUser> login(String username, String password) {
        Instant now = clock.instant();
        Optional<AppUser> found = users.findWithLockByUsernameIgnoreCase(username == null ? "" : username.trim());

        if (found.isEmpty()) {
            passwordMatches(password, unknownUserHash);
            auditFailure(username == null ? null : username.trim(), null, null, "UNKNOWN_USER");
            return Optional.empty();
        }

        AppUser user = found.get();
        boolean matches = passwordMatches(password, user.getPasswordHash());

        if (user.isLockedAt(now)) {
            auditFailure(user.getUsername(), user.getId(), user.getRole().name(), "LOCKED");
            return Optional.empty();
        }
        if (!user.isActive()) {
            auditFailure(user.getUsername(), user.getId(), user.getRole().name(), "INACTIVE");
            return Optional.empty();
        }
        if (!matches) {
            user.recordFailedLogin(now, properties.getMaxFailedAttempts(), properties.lockDuration());
            // The account was not locked when we got here, so a lock now means this failure caused it.
            if (user.isLockedAt(now)) {
                log.warn("Account locked after repeated failed logins: userId={}", user.getId());
            }
            auditFailure(user.getUsername(), user.getId(), user.getRole().name(), "BAD_PASSWORD");
            return Optional.empty();
        }

        user.recordSuccessfulLogin(now);
        audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.SUCCESS)
            .by(user.getId(), user.getUsername(), user.getRole().name()));
        return Optional.of(new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(),
            user.getRole(), user.isMustChangePassword()));
    }

    /** The reason is for administrators only; the client always gets the same 401. */
    private void auditFailure(String username, Long userId, String role, String reason) {
        audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.FAILURE)
            .by(userId, username, role).detail("reason", reason));
    }

    /** Passwords over 72 bytes can never match a BCrypt hash made by this system; no need to ask BCrypt. */
    private boolean passwordMatches(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            passwordEncoder.matches("x", hash);
            return false;
        }
        return passwordEncoder.matches(password, hash);
    }
}
