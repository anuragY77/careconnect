package com.careconnect.patient.api;

import java.util.List;

import com.careconnect.patient.service.ChartSummary;

public record ChartSummaryResponse(PatientResponse patient, List<AllergyResponse> allergies,
                                   List<ProblemResponse> problems) {

    static ChartSummaryResponse from(ChartSummary s) {
        return new ChartSummaryResponse(PatientResponse.from(s.patient()),
            s.allergies().stream().map(AllergyResponse::from).toList(),
            s.problems().stream().map(ProblemResponse::from).toList());
    }
}
