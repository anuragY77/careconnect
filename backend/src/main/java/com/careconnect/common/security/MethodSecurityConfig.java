package com.careconnect.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on {@code @PreAuthorize}. Together with the filter chain this gives two locks: the chain
 * decides who is signed in, the annotation on each endpoint decides which roles may call it.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
