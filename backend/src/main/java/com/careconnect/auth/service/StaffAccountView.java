package com.careconnect.auth.service;

import java.time.Instant;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;

/** A staff account as the admin screens see it. Never contains the password hash. */
public record StaffAccountView(long userId, String username, String fullName, String email, Role role,
                               UserStatus status, boolean mustChangePassword, boolean locked,
                               Instant lastLoginAt) {

    static StaffAccountView from(AppUser user, Instant now) {
        return new StaffAccountView(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
            user.getRole(), user.getStatus(), user.isMustChangePassword(), user.isLockedAt(now),
            user.getLastLoginAt());
    }
}
