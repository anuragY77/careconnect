package com.careconnect.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshCookiesTest {

    @Test
    void issuedCookieHasAllSecurityAttributes() {
        ResponseCookie cookie = RefreshCookies.issue("token-value", Duration.ofDays(7));
        String header = cookie.toString();

        assertThat(cookie.getName()).isEqualTo("cc_refresh");
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
    }

    @Test
    void clearingCookieExpiresItImmediately() {
        ResponseCookie cookie = RefreshCookies.clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
    }
}
