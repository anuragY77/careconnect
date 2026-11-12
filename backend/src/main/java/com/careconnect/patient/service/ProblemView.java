package com.careconnect.patient.service;

import java.time.Instant;
import java.time.LocalDate;

import com.careconnect.patient.domain.Problem;
import com.careconnect.patient.domain.ProblemStatus;

public record ProblemView(long problemId, long patientId, String icd10Code, String description, LocalDate onsetDate,
                          ProblemStatus status, String statusReason, long recordedBy, Instant recordedAt,
                          long version) {

    static ProblemView from(Problem p) {
        return new ProblemView(p.getId(), p.getPatientId(), p.getIcd10Code(), p.getDescription(), p.getOnsetDate(),
            p.getStatus(), p.getStatusReason(), p.getRecordedBy(), p.getRecordedAt(), p.getVersion());
    }
}
