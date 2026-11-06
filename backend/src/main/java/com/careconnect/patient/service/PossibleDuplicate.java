package com.careconnect.patient.service;

import java.time.LocalDate;

import com.careconnect.patient.domain.Patient;

/** An existing patient that looks like the one being registered; enough to tell them apart, nothing more. */
public record PossibleDuplicate(long patientId, String mrn, String firstName, String lastName,
                                LocalDate dateOfBirth) {

    static PossibleDuplicate from(Patient p) {
        return new PossibleDuplicate(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(), p.getDateOfBirth());
    }
}
