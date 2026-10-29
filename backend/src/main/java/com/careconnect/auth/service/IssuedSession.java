package com.careconnect.auth.service;

import java.time.Instant;

/**
 * What a successful login or refresh hands to the web layer: the user, a new access token and
 * the new refresh token (to be sent only in the cookie) with its expiry.
 */
public record IssuedSession(AuthenticatedUser user, JwtService.AccessToken accessToken,
                            String refreshToken, Instant refreshExpiresAt) {

    /** Keeps both tokens out of logs. */
    @Override
    public String toString() {
        return "IssuedSession[userId=" + user.userId() + "]";
    }
}
