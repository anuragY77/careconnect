package com.careconnect.patient.service;

import java.time.LocalDate;

import com.careconnect.patient.domain.Sex;

/** Demographics entered to register or to update a patient. Optional values may be null or blank. */
public record NewPatient(String firstName, String lastName, LocalDate dateOfBirth, Sex sex, String phone,
                         String email, String address, String emergencyContactName,
                         String emergencyContactPhone, String insuranceProvider, String insurancePolicyNumber) {
}
