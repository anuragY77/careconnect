package com.careconnect.patient.api;

import java.time.Instant;

import com.careconnect.patient.domain.AllergySeverity;
import com.careconnect.patient.domain.AllergyStatus;
import com.careconnect.patient.service.AllergyView;

/** One allergy. {@code version} is sent back with a status change to detect changes by someone else. */
public record AllergyResponse(long allergyId, long patientId, String substance, String reaction,
                              AllergySeverity severity, AllergyStatus status, String statusReason, long recordedBy,
                              Instant recordedAt, long version) {

    static AllergyResponse from(AllergyView v) {
        return new AllergyResponse(v.allergyId(), v.patientId(), v.substance(), v.reaction(), v.severity(),
            v.status(), v.statusReason(), v.recordedBy(), v.recordedAt(), v.version());
    }
}
