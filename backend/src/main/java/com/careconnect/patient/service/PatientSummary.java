package com.careconnect.patient.service;

import java.time.LocalDate;

import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientStatus;
import com.careconnect.patient.domain.Sex;

/** One line of a search result: enough to pick the right patient, no address, insurance or contacts. */
public record PatientSummary(long patientId, String mrn, String firstName, String lastName, LocalDate dateOfBirth,
                             Sex sex, String phone, PatientStatus status) {

    static PatientSummary from(Patient p) {
        return new PatientSummary(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(), p.getDateOfBirth(),
            p.getSex(), p.getPhone(), p.getStatus());
    }
}
