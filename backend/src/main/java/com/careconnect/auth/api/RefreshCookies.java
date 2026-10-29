package com.careconnect.auth.api;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * The cookie that carries the refresh token. JavaScript cannot read it (HttpOnly), it travels
 * only over HTTPS (Secure), it is never sent on a cross-site request (SameSite=Strict) and the
 * browser sends it only to the auth endpoints (Path), not to every API call.
 */
final class RefreshCookies {

    static final String NAME = "cc_refresh";
    static final String PATH = "/api/auth";

    private RefreshCookies() {
    }

    static ResponseCookie issue(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    /** An expired cookie with the same name and path, which makes the browser delete it. */
    static ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(PATH);
    }
}
