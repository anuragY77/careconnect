package com.careconnect.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A person who can sign in: staff, or a patient with a portal account.
 * The password is stored only as a hash. The lockout rules live here, so
 * no other class can change the failed-attempt counter in a different way.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "linked_patient_id")
    private Long linkedPatientId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. */
    protected AppUser() {
    }

    private AppUser(String username, String passwordHash, String fullName, String email,
                    Role role, Long linkedPatientId, boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.linkedPatientId = linkedPatientId;
        this.mustChangePassword = mustChangePassword;
    }

    /**
     * Creates a new active user. A PATIENT must be linked to a patient id and every
     * other role must not be (the database enforces the same rule).
     */
    public static AppUser create(String username, String passwordHash, String fullName, String email,
                                 Role role, Long linkedPatientId, boolean mustChangePassword) {
        requireText(username, "username");
        requireText(passwordHash, "passwordHash");
        requireText(fullName, "fullName");
        requireText(email, "email");
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (role == Role.PATIENT && linkedPatientId == null) {
            throw new IllegalArgumentException("A PATIENT user must be linked to a patient");
        }
        if (role != Role.PATIENT && linkedPatientId != null) {
            throw new IllegalArgumentException("Only a PATIENT user can be linked to a patient");
        }
        return new AppUser(username.trim(), passwordHash, fullName.trim(), email.trim(),
            role, linkedPatientId, mustChangePassword);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /** True while a temporary lockout is running. The lockout ends exactly at {@code lockedUntil}. */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Counts a failed login. When the count reaches {@code maxAttempts} the account is locked
     * for {@code lockDuration}. Attempts made while the account is locked change nothing, so an
     * attacker cannot extend the lock of a real user forever. After a lock has ended the
     * count starts again from zero.
     */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
        if (isLockedAt(now)) {
            return;
        }
        if (lockedUntil != null) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    /** Clears the failed-attempt counter and any finished lock, and remembers the login time. */
    public void recordSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Long getLinkedPatientId() {
        return linkedPatientId;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
