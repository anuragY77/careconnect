package com.careconnect.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ApiSecurityErrorHandlerTest {

    private final ApiSecurityErrorHandler handler = new ApiSecurityErrorHandler();

    @Test
    void unauthenticatedIsA401ProblemWithBearerChallenge() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("secret detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        String body = response.getContentAsString();
        assertThat(body).contains("\"status\":401", "\"code\":\"UNAUTHENTICATED\"",
            "\"type\":\"urn:careconnect:error:unauthenticated\"", "\"timestamp\":\"");
        assertThat(body).contains(ApiSecurityErrorHandler.UNAUTHENTICATED_DETAIL);
        // The reason inside the exception never reaches the client.
        assertThat(body).doesNotContain("secret detail");
    }

    @Test
    void accessDeniedIsA403Problem() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("internal rule"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        String body = response.getContentAsString();
        assertThat(body).contains("\"status\":403", "\"code\":\"ACCESS_DENIED\"");
        assertThat(body).contains(ApiSecurityErrorHandler.ACCESS_DENIED_DETAIL);
        assertThat(body).doesNotContain("internal rule");
    }
}
