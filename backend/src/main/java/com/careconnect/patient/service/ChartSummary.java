package com.careconnect.patient.service;

import java.util.List;

public record ChartSummary(PatientView patient, List<AllergyView> allergies, List<ProblemView> problems) {
}
