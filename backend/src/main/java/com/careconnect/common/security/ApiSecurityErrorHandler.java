package com.careconnect.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.careconnect.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes the 401 and 403 answers of the security filter chain in the same RFC 7807 format as the
 * rest of the API. These failures happen in a filter, before any controller, so the global
 * exception handler never sees them. The body is built only from fixed texts, never from request data.
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHENTICATED_DETAIL = "Authentication is required to access this resource.";
    static final String ACCESS_DENIED_DETAIL = "You do not have permission to perform this action.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, ErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_DETAIL);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, ErrorCode.ACCESS_DENIED, ACCESS_DENIED_DETAIL);
    }

    private static void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        String body = "{\"type\":\"" + code.typeUri() + "\""
            + ",\"title\":\"" + code.title() + "\""
            + ",\"status\":" + code.status()
            + ",\"detail\":\"" + detail + "\""
            + ",\"code\":\"" + code.name() + "\""
            + ",\"timestamp\":\"" + Instant.now() + "\"}";
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
