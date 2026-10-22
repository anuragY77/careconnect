package com.careconnect.auth.service;

import com.careconnect.auth.domain.Role;

/** The result of a successful login. Carries no password data. */
public record AuthenticatedUser(long userId, String username, String fullName, Role role,
                                boolean mustChangePassword) {
}
