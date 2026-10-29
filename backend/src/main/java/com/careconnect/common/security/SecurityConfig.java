package com.careconnect.common.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * The security filter chain: stateless, deny by default.
 *
 * <ul>
 *   <li>Public: the health probe and POST login, refresh and logout.</li>
 *   <li>Everything under {@code /api/} needs a valid access token (HS256, our issuer, not expired).</li>
 *   <li>Everything else is denied.</li>
 * </ul>
 *
 * <p>Access tokens are read only from the {@code Authorization: Bearer} header, never from a URL.
 * The three public auth calls ignore the header on purpose: the web app calls refresh exactly when
 * its access token has expired, and that request must not fail because of the expired token.
 *
 * <p>There is no CORS configuration: the web app and the API share one origin behind nginx, so
 * browsers never make cross-origin calls and cross-origin requests stay blocked. CSRF protection is
 * off because no endpoint authorises by cookie except refresh and logout, which rely on
 * SameSite=Strict and the Origin check.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Set<String> PUBLIC_AUTH_POSTS =
        Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            ApiSecurityErrorHandler errors) throws Exception {
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(new RoleClaimConverter());

        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        BearerTokenResolver resolver = request ->
            isPublicAuthCall(request) ? null : headerResolver.resolve(request);

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors))
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(resolver)
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors)
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authentication)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                    .permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        return http.build();
    }

    private static boolean isPublicAuthCall(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && PUBLIC_AUTH_POSTS.contains(request.getRequestURI());
    }
}
