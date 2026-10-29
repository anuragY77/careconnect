package com.careconnect.common.web;

import java.net.URI;
import java.util.Locale;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Second line of defence for the cookie-based endpoints (login, refresh, logout), next to
 * SameSite=Strict: when the browser says which site the request comes from (the Origin header),
 * it must be our own web app. A request without an Origin header (curl, server-to-server) is
 * allowed, because only a browser can be tricked into sending the cookie, and browsers always
 * send Origin on cross-site POST requests.
 */
@Component
public class OriginCheck {

    private static final Logger log = LoggerFactory.getLogger(OriginCheck.class);

    private final String allowedOrigin;

    public OriginCheck(FrontendProperties properties) {
        this.allowedOrigin = normalise(properties.getOrigin());
    }

    public void requireAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return;
        }
        if (!allowedOrigin.equals(origin.trim().toLowerCase(Locale.ROOT))) {
            log.warn("Request from a foreign origin rejected: origin={}", printable(origin));
            throw new ApiException(ErrorCode.ACCESS_DENIED, "This request is not allowed from this site.");
        }
    }

    /** Reduces a configured URL to {@code scheme://host[:port]}, without default ports. */
    static String normalise(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("FRONTEND_ORIGIN is not set.");
        }
        String text = configured.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("FRONTEND_ORIGIN is not a valid URL: " + printable(configured), e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean hasExtras = (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
            || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null;
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null || hasExtras) {
            throw new IllegalStateException(
                "FRONTEND_ORIGIN must look like https://host or http://host:port, without a path: "
                    + printable(configured));
        }
        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return scheme + "://" + host + (port == -1 || defaultPort ? "" : ":" + port);
    }

    /** Header values come from the client; keep them short and free of control characters in logs. */
    private static String printable(String value) {
        String cleaned = value.replaceAll("[^\\x20-\\x7E]", "?");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "..." : cleaned;
    }
}
