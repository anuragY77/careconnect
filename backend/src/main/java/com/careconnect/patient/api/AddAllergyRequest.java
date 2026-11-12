package com.careconnect.patient.api;

import com.careconnect.patient.domain.AllergySeverity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/patients/{patientId}/allergies. Who recorded it is the signed-in user, never a field. */
public record AddAllergyRequest(
    @NotBlank @Size(max = 120) String substance,
    @Size(max = 200) String reaction,
    @NotNull AllergySeverity severity) {
}
