package com.careconnect.patient.api;

import java.time.Instant;
import java.time.LocalDate;

import com.careconnect.patient.domain.ProblemStatus;
import com.careconnect.patient.service.ProblemView;

public record ProblemResponse(long problemId, long patientId, String icd10Code, String description,
                              LocalDate onsetDate, ProblemStatus status, String statusReason, long recordedBy,
                              Instant recordedAt, long version) {

    static ProblemResponse from(ProblemView v) {
        return new ProblemResponse(v.problemId(), v.patientId(), v.icd10Code(), v.description(), v.onsetDate(),
            v.status(), v.statusReason(), v.recordedBy(), v.recordedAt(), v.version());
    }
}
