package com.careconnect.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One refresh token of a sign-in session. The token itself is never stored, only its hash.
 * A token can be used once: using it marks it as used and a new token of the same family
 * takes its place. All tokens of one sign-in share a {@code familyId}.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Required by JPA. */
    protected RefreshToken() {
    }

    private RefreshToken(Long userId, String tokenHash, UUID familyId, Instant issuedAt,
                         Instant expiresAt, Instant familyExpiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
    }

    /**
     * Creates a new, unused token. The token expires at {@code expiresAt}, which can never be
     * later than the end of the whole family (the database enforces the same rule).
     */
    public static RefreshToken issue(Long userId, String tokenHash, UUID familyId, Instant issuedAt,
                                     Instant expiresAt, Instant familyExpiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash is required");
        }
        if (familyId == null || issuedAt == null || expiresAt == null || familyExpiresAt == null) {
            throw new IllegalArgumentException("familyId and all times are required");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (expiresAt.isAfter(familyExpiresAt)) {
            throw new IllegalArgumentException("expiresAt must not be after familyExpiresAt");
        }
        return new RefreshToken(userId, tokenHash, familyId, issuedAt, expiresAt, familyExpiresAt);
    }

    /** True once the token's own time or the end of the whole family has been reached. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Remembers that the token was exchanged for a new one. The first time wins. */
    public void markUsed(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
