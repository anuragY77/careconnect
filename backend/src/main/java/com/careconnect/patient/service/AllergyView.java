package com.careconnect.patient.service;

import java.time.Instant;

import com.careconnect.patient.domain.Allergy;
import com.careconnect.patient.domain.AllergySeverity;
import com.careconnect.patient.domain.AllergyStatus;

/** One allergy as the screens see it. */
public record AllergyView(long allergyId, long patientId, String substance, String reaction,
                          AllergySeverity severity, AllergyStatus status, String statusReason, long recordedBy,
                          Instant recordedAt, long version) {

    static AllergyView from(Allergy a) {
        return new AllergyView(a.getId(), a.getPatientId(), a.getSubstance(), a.getReaction(), a.getSeverity(),
            a.getStatus(), a.getStatusReason(), a.getRecordedBy(), a.getRecordedAt(), a.getVersion());
    }
}
