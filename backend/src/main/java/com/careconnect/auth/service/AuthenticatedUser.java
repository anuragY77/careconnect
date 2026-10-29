package com.careconnect.auth.service;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;

/** The result of a successful login. Carries no password data. */
public record AuthenticatedUser(long userId, String username, String fullName, Role role,
                                boolean mustChangePassword) {

    /** Copies the public facts of a stored user. The password hash is never copied. */
    static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(),
            user.getRole(), user.isMustChangePassword());
    }
}
