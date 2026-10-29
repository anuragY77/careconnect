package com.careconnect.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.RefreshToken;
import com.careconnect.auth.repository.AppUserRepository;
import com.careconnect.auth.repository.RefreshTokenRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts, renews and ends sign-in sessions.
 *
 * <p>A refresh token is 256 random bits and is stored only as a SHA-256 hash. It can be used
 * once. Using it returns a new token of the same family and marks the old one as used. If a
 * used token shows up again, someone copied it, and we cannot tell the owner from the thief,
 * so the whole family is revoked and both must sign in again. Two requests with the same token
 * at the same moment count as reuse too: the row lock makes one of them go second.
 *
 * <p>A token expires after the idle time (JWT_REFRESH_TTL_DAYS), but a family never lives
 * longer than JWT_REFRESH_MAX_DAYS from the sign-in, however often it is renewed.
 *
 * <p>{@link #rotate} returns an empty result for every failure instead of throwing, so the
 * revocation it performs is committed and not rolled back by an exception.
 */
@Service
public class TokenService {

    /** A real token is 43 characters; anything much longer is not worth hashing. */
    static final int MAX_TOKEN_LENGTH = 128;

    /** Expired tokens are kept this long after expiry before they are deleted. */
    static final Duration PURGE_GRACE = Duration.ofDays(1);

    private static final int TOKEN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final RefreshTokenRepository tokens;
    private final AppUserRepository users;
    private final JwtService jwtService;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(RefreshTokenRepository tokens, AppUserRepository users, JwtService jwtService,
                        JwtProperties properties, Clock clock) {
        if (properties.getRefreshMaxDays() < properties.getRefreshTtlDays()) {
            throw new IllegalStateException("JWT_REFRESH_MAX_DAYS must not be smaller than JWT_REFRESH_TTL_DAYS.");
        }
        this.tokens = tokens;
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new session (a new token family) for a user who just proved who they are. */
    @Transactional
    public IssuedSession startSession(AuthenticatedUser user) {
        Instant now = clock.instant();
        Instant familyEnd = now.plus(properties.refreshMaxLifetime());
        Instant expires = earlier(now.plus(properties.refreshIdleTtl()), familyEnd);
        String token = newTokenValue();
        tokens.save(RefreshToken.issue(user.userId(), hash(token), UUID.randomUUID(), now, expires, familyEnd));
        return new IssuedSession(user, jwtService.issue(user), token, expires);
    }

    /**
     * Exchanges a refresh token for a new session. Returns empty for an unknown, expired, revoked
     * or reused token, and for a user who is no longer active. The caller cannot tell the cases
     * apart, and neither can a client.
     */
    @Transactional
    public Optional<IssuedSession> rotate(String presented) {
        if (!plausible(presented)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<RefreshToken> found = tokens.findWithLockByTokenHash(hash(presented));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();

        if (current.isRevoked()) {
            return Optional.empty();
        }
        if (current.isUsed()) {
            tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected, session family revoked: userId={}, familyId={}",
                current.getUserId(), current.getFamilyId());
            return Optional.empty();
        }
        if (current.isExpiredAt(now)) {
            return Optional.empty();
        }

        Optional<AppUser> user = users.findById(current.getUserId());
        if (user.isEmpty() || !user.get().isActive()) {
            tokens.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }

        current.markUsed(now);
        Instant expires = earlier(now.plus(properties.refreshIdleTtl()), current.getFamilyExpiresAt());
        String next = newTokenValue();
        tokens.save(RefreshToken.issue(current.getUserId(), hash(next), current.getFamilyId(), now, expires,
            current.getFamilyExpiresAt()));

        AuthenticatedUser authenticated = AuthenticatedUser.from(user.get());
        return Optional.of(new IssuedSession(authenticated, jwtService.issue(authenticated), next, expires));
    }

    /** Ends the session the token belongs to. An unknown or malformed token is ignored. */
    @Transactional
    public void endSession(String presented) {
        if (!plausible(presented)) {
            return;
        }
        Instant now = clock.instant();
        tokens.findWithLockByTokenHash(hash(presented))
            .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), now));
    }

    /** Deletes tokens that expired more than a day ago. Returns how many were deleted. */
    @Transactional
    public int purgeExpired() {
        return tokens.purgeExpiredBefore(clock.instant().minus(PURGE_GRACE));
    }

    private static boolean plausible(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 lowercase hex characters, the form the database column expects. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
