package com.careconnect.patient.service;

import java.time.Instant;
import java.time.LocalDate;

import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientStatus;
import com.careconnect.patient.domain.Sex;

/** A patient's demographics as the screens see them. */
public record PatientView(long patientId, String mrn, String firstName, String lastName, LocalDate dateOfBirth,
                          Sex sex, String phone, String email, String address, String emergencyContactName,
                          String emergencyContactPhone, String insuranceProvider, String insurancePolicyNumber,
                          PatientStatus status, Instant createdAt, long version) {

    static PatientView from(Patient p) {
        return new PatientView(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(), p.getDateOfBirth(),
            p.getSex(), p.getPhone(), p.getEmail(), p.getAddress(), p.getEmergencyContactName(),
            p.getEmergencyContactPhone(), p.getInsuranceProvider(), p.getInsurancePolicyNumber(),
            p.getStatus(), p.getCreatedAt(), p.getVersion());
    }
}
