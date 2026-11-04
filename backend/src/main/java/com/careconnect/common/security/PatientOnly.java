package com.careconnect.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Patients only (portal accounts).
 *
 * <p>Put it on an endpoint method. A test checks the roles named here against the {@code Role}
 * enum, so a new role cannot be forgotten.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('PATIENT')")
public @interface PatientOnly {
}
