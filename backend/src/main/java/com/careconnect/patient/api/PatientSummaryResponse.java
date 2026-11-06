package com.careconnect.patient.api;

import java.time.LocalDate;

import com.careconnect.patient.domain.PatientStatus;
import com.careconnect.patient.domain.Sex;
import com.careconnect.patient.service.PatientSummary;

/** One line of a search result. */
public record PatientSummaryResponse(long patientId, String mrn, String firstName, String lastName,
                                     LocalDate dateOfBirth, Sex sex, String phone, PatientStatus status) {

    static PatientSummaryResponse from(PatientSummary s) {
        return new PatientSummaryResponse(s.patientId(), s.mrn(), s.firstName(), s.lastName(), s.dateOfBirth(),
            s.sex(), s.phone(), s.status());
    }
}
