package com.careconnect.patient.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddProblemRequest(
    @NotBlank @Size(max = 10) String icd10Code,
    LocalDate onsetDate) {
}
