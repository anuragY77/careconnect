package com.careconnect.patient.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
 *
 * <p>A possible duplicate (same date of birth and the same name or phone number) is warned about,
 * not forbidden: twins and families share details, so staff confirm and the confirmation is audited.
 */
@Service
public class PatientService {

    /** Prefix of every MRN, followed by the sequence number (for example CC-100001). */
    static final String MRN_PREFIX = "CC-";

    /** Shorter numbers are not compared: they are extensions or typing mistakes, not a phone number. */
    private static final int MIN_PHONE_DIGITS = 7;

    private final PatientRepository patients;
    private final AuditService audit;
    private final Clock clock;

    public PatientService(PatientRepository patients, AuditService audit, Clock clock) {
        this.patients = patients;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Registers a patient. When possible duplicates exist and {@code confirmedNotDuplicate} is false,
     * nothing is saved and a POSSIBLE_DUPLICATE error lists them.
     */
    @Transactional
    public PatientView register(NewPatient input, boolean confirmedNotDuplicate) {
        String firstName = name(input.firstName(), "First name");
        String lastName = name(input.lastName(), "Last name");
        requireValidDateOfBirth(input.dateOfBirth());
        if (input.sex() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sex is required.");
        }
        String phone = optional(input.phone());
        List<Patient> similar = findSimilar(firstName, lastName, input.dateOfBirth(), phone);
        if (!similar.isEmpty() && !confirmedNotDuplicate) {
            throw new ApiException(ErrorCode.POSSIBLE_DUPLICATE,
                "A patient with the same date of birth and the same name or phone number is already registered. "
                    + "Check the list, then confirm to register anyway.")
                .with("possibleDuplicates", similar.stream().map(PossibleDuplicate::from).toList());
        }
        String mrn = MRN_PREFIX + patients.nextMrnNumber();
        Patient saved = patients.save(Patient.register(mrn, firstName, lastName, input.dateOfBirth(), input.sex(),
            phone, lowerOptional(input.email()), optional(input.address()),
            optional(input.emergencyContactName()), optional(input.emergencyContactPhone()),
            optional(input.insuranceProvider()), optional(input.insurancePolicyNumber())));
        AuditEntry entry = AuditEntry.of(AuditAction.PATIENT_REGISTERED, AuditOutcome.SUCCESS)
            .on("PATIENT", saved.getId()).patient(saved.getId()).detail("mrn", saved.getMrn());
        if (!similar.isEmpty()) {
            entry.detail("duplicateConfirmed", true)
                .detail("similarMrns", similar.stream().map(Patient::getMrn).collect(Collectors.joining(",")));
        }
        audit.record(entry);
        return PatientView.from(saved);
    }

    /**
     * Same date of birth and the same name, or the same date of birth and the same phone number.
     * Two staff registering the same person at the same moment can both pass this check; the warning
     * is a help for the front desk, not a database rule.
     */
    private List<Patient> findSimilar(String firstName, String lastName, LocalDate dob, String phone) {
        Map<Long, Patient> found = new LinkedHashMap<>();
        patients.findTop5ByDateOfBirthAndFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderById(dob, firstName, lastName)
            .forEach(p -> found.put(p.getId(), p));
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() >= MIN_PHONE_DIGITS) {
            // shortcut: full digit strings are compared, so "+91 98765 43210" and "98765 43210" differ; normalise country codes if staff report misses
            patients.findByDateOfBirthAndPhoneDigits(dob, digits).forEach(p -> found.putIfAbsent(p.getId(), p));
        }
        return found.values().stream().limit(5).toList();
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
