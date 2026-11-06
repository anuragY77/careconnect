package com.careconnect.patient.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.audit.service.AuditEntry;
import com.careconnect.audit.service.AuditService;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.repository.PatientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient registry rules (FR-05 to FR-07). Registration creates the patient, gives it the next MRN
 * and writes the audit entry in one transaction: if the audit write fails, the patient is not saved.
 */
@Service
public class PatientService {

    /** Prefix of every MRN, followed by the sequence number (for example CC-100001). */
    static final String MRN_PREFIX = "CC-";

    private final PatientRepository patients;
    private final AuditService audit;
    private final Clock clock;

    public PatientService(PatientRepository patients, AuditService audit, Clock clock) {
        this.patients = patients;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PatientView register(NewPatient input) {
        String firstName = name(input.firstName(), "First name");
        String lastName = name(input.lastName(), "Last name");
        requireValidDateOfBirth(input.dateOfBirth());
        if (input.sex() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sex is required.");
        }
        String mrn = MRN_PREFIX + patients.nextMrnNumber();
        Patient saved = patients.save(Patient.register(mrn, firstName, lastName, input.dateOfBirth(), input.sex(),
            optional(input.phone()), lowerOptional(input.email()), optional(input.address()),
            optional(input.emergencyContactName()), optional(input.emergencyContactPhone()),
            optional(input.insuranceProvider()), optional(input.insurancePolicyNumber())));
        audit.record(AuditEntry.of(AuditAction.PATIENT_REGISTERED, AuditOutcome.SUCCESS)
            .on("PATIENT", saved.getId()).patient(saved.getId()).detail("mrn", saved.getMrn()));
        return PatientView.from(saved);
    }

    /** Not in the future. One day of slack, because the clock and the database run in UTC (V6). */
    private void requireValidDateOfBirth(LocalDate dob) {
        if (dob == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Date of birth is required.");
        }
        LocalDate latest = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
        if (dob.isAfter(latest)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Date of birth cannot be in the future.");
        }
        if (dob.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Date of birth cannot be before 1900.");
        }
    }

    private static String name(String value, String label) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + " is required.");
        }
        return clean;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String lowerOptional(String value) {
        String clean = optional(value);
        return clean == null ? null : clean.toLowerCase(Locale.ROOT);
    }
}
