package com.careconnect.patient.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Staff who look patients up to open a chart: the front desk, doctors and nurses (UC-05). Pharmacists and
 * laboratory technicians reach patients through their own queues; administrators see no clinical data.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE')")
public @interface PatientSearchStaff {
}
