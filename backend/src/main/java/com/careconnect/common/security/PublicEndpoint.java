package com.careconnect.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that anyone may call without signing in (login, refresh, logout). It changes
 * nothing at run time: the route must also be listed as public in {@link SecurityConfig}. Its job
 * is to make "public" a visible, deliberate choice, so the architecture test can fail the build for
 * an endpoint that declares no authorization rule at all.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
