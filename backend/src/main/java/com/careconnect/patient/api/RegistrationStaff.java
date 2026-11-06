package com.careconnect.patient.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Staff who register and update patients: the front desk, doctors (walk-ins, BR-10) and nurses.
 * Pharmacists, laboratory technicians and administrators read charts but do not register patients.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE')")
public @interface RegistrationStaff {
}
