package com.careconnect.patient.api;

import com.careconnect.patient.domain.AllergyStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/allergies/{id}/status. A reason is required unless the allergy becomes active again.
 * {@code version} is the version the screen was showing.
 */
public record ChangeAllergyStatusRequest(
    @NotNull Long version,
    @NotNull AllergyStatus status,
    @Size(max = 200) String reason) {
}
