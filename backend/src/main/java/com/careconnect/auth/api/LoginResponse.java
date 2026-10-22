package com.careconnect.auth.api;

import com.careconnect.auth.domain.Role;

/** Body of a successful login. The access token is added by TASK-016. */
public record LoginResponse(long userId, String username, String fullName, Role role, boolean mustChangePassword) {
}
