package com.careconnect.auth.domain;

/** Account status. A temporary lockout after failed logins is not a status; it is {@code lockedUntil}. */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
