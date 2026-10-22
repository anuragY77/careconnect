package com.careconnect.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/login. The size limits only stop absurdly large input. */
public record LoginRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(max = 128) String password) {

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
