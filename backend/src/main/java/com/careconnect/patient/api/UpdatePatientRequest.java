package com.careconnect.patient.api;

import java.time.LocalDate;

import com.careconnect.patient.domain.Sex;
import com.careconnect.patient.service.NewPatient;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/patients/{id}: the whole set of demographics, not a patch. {@code version} is the
 * version the screen was showing; the update is refused if someone changed the patient since. The MRN
 * is not part of the body and can never change.
 */
public record UpdatePatientRequest(
    @NotNull Long version,
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotNull LocalDate dateOfBirth,
    @NotNull Sex sex,
    @Size(max = 20) @Pattern(regexp = PatientFieldRules.PHONE, message = PatientFieldRules.PHONE_MESSAGE)
    String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 300) String address,
    @Size(max = 120) String emergencyContactName,
    @Size(max = 20) @Pattern(regexp = PatientFieldRules.PHONE, message = PatientFieldRules.PHONE_MESSAGE)
    String emergencyContactPhone,
    @Size(max = 120) String insuranceProvider,
    @Size(max = 60) String insurancePolicyNumber) {

    NewPatient toCommand() {
        return new NewPatient(firstName, lastName, dateOfBirth, sex, phone, email, address, emergencyContactName,
            emergencyContactPhone, insuranceProvider, insurancePolicyNumber);
    }
}
