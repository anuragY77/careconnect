package com.careconnect.patient.api;

import java.time.Instant;
import java.time.LocalDate;

import com.careconnect.patient.domain.PatientStatus;
import com.careconnect.patient.domain.Sex;
import com.careconnect.patient.service.PatientView;

/** One patient's demographics. */
public record PatientResponse(long patientId, String mrn, String firstName, String lastName, LocalDate dateOfBirth,
                              Sex sex, String phone, String email, String address, String emergencyContactName,
                              String emergencyContactPhone, String insuranceProvider,
                              String insurancePolicyNumber, PatientStatus status, Instant createdAt, long version) {

    static PatientResponse from(PatientView v) {
        return new PatientResponse(v.patientId(), v.mrn(), v.firstName(), v.lastName(), v.dateOfBirth(), v.sex(),
            v.phone(), v.email(), v.address(), v.emergencyContactName(), v.emergencyContactPhone(),
            v.insuranceProvider(), v.insurancePolicyNumber(), v.status(), v.createdAt(), v.version());
    }
}
