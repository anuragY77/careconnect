package com.careconnect.patient.service;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class PatientAccessPolicy {

    public ChartScope scopeFor(ChartActor actor, long patientId) {
        if (actor == null || actor.role() == null) {
            return ChartScope.NONE;
        }
        return switch (actor.role()) {
            case "DOCTOR", "NURSE" -> ChartScope.FULL;
            case "RECEPTIONIST" -> ChartScope.DEMOGRAPHICS;
            case "PATIENT" -> actor.patientId() != null && actor.patientId() == patientId
                ? ChartScope.FULL : ChartScope.NONE;
            default -> ChartScope.NONE;
        };
    }

    public void require(ChartActor actor, long patientId, ChartScope needed) {
        if (!scopeFor(actor, patientId).covers(needed)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.");
        }
    }
}
