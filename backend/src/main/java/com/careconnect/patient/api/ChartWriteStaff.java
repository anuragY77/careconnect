package com.careconnect.patient.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Clinical staff who write to the patient chart: doctors and nurses. The front desk registers patients
 * but does not record allergies or other clinical facts; pharmacists and laboratory technicians work from
 * their own queues; administrators see no clinical data.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('DOCTOR', 'NURSE')")
public @interface ChartWriteStaff {
}
