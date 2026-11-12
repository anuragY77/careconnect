package com.careconnect.patient.api;

import com.careconnect.patient.domain.ProblemStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeProblemStatusRequest(
    @NotNull Long version,
    @NotNull ProblemStatus status,
    @Size(max = 200) String reason) {
}
