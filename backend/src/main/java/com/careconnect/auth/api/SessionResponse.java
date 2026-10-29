package com.careconnect.auth.api;

import com.careconnect.auth.domain.Role;
import com.careconnect.auth.service.IssuedSession;

/**
 * Body of a successful login or refresh. The access token goes in the body (the web app keeps
 * it in memory only); the refresh token is never in the body, only in the HttpOnly cookie.
 */
public record SessionResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    /** The signed-in user, as the web app needs it. Never contains password data. */
    public record UserSummary(long userId, String username, String fullName, Role role,
                              boolean mustChangePassword) {
    }

    static SessionResponse from(IssuedSession session) {
        return new SessionResponse(
            session.accessToken().value(),
            "Bearer",
            session.accessToken().expiresInSeconds(),
            new UserSummary(session.user().userId(), session.user().username(), session.user().fullName(),
                session.user().role(), session.user().mustChangePassword()));
    }

    /** Keeps the token out of logs. */
    @Override
    public String toString() {
        return "SessionResponse[user=" + user + "]";
    }
}
