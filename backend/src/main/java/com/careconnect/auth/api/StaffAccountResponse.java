package com.careconnect.auth.api;

import java.time.Instant;

import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;
import com.careconnect.auth.service.StaffAccountView;

/** One staff account. Never contains password data. */
public record StaffAccountResponse(long userId, String username, String fullName, String email, Role role,
                                   UserStatus status, boolean mustChangePassword, boolean locked,
                                   Instant lastLoginAt) {

    static StaffAccountResponse from(StaffAccountView v) {
        return new StaffAccountResponse(v.userId(), v.username(), v.fullName(), v.email(), v.role(),
            v.status(), v.mustChangePassword(), v.locked(), v.lastLoginAt());
    }
}
