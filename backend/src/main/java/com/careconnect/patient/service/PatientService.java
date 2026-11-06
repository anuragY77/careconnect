package com.careconnect.patient.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
     * Replaces a patient's demographics (FR-07). {@code expectedVersion} is the version the caller saw; a
     * different version means someone else changed the patient meanwhile and nothing is saved. The audit
     * entry keeps before and after values of the identifying fields, and for address, emergency contact and
     * insurance (free text and policy numbers) only the fact that they changed. A request that changes
     * nothing saves and records nothing.
     */
    @Transactional
    public PatientView update(long patientId, long expectedVersion, NewPatient input) {
        Patient patient = patients.findById(patientId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found."));
        if (patient.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "The patient was changed by someone else. Reload the record and try again.");
        }
        String firstName = name(input.firstName(), "First name");
        String lastName = name(input.lastName(), "Last name");
        requireValidDateOfBirth(input.dateOfBirth());
        if (input.sex() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sex is required.");
        }
        String phone = optional(input.phone());
        String email = lowerOptional(input.email());
        String address = optional(input.address());
        String contactName = optional(input.emergencyContactName());
        String contactPhone = optional(input.emergencyContactPhone());
        String insurer = optional(input.insuranceProvider());
        String policy = optional(input.insurancePolicyNumber());

        List<String> changed = new ArrayList<>();
        AuditEntry entry = AuditEntry.of(AuditAction.PATIENT_UPDATED, AuditOutcome.SUCCESS)
            .on("PATIENT", patient.getId()).patient(patient.getId()).detail("mrn", patient.getMrn());
        trackChange(changed, entry, "firstName", patient.getFirstName(), firstName, true);
        trackChange(changed, entry, "lastName", patient.getLastName(), lastName, true);
        trackChange(changed, entry, "dateOfBirth", patient.getDateOfBirth(), input.dateOfBirth(), true);
        trackChange(changed, entry, "sex", patient.getSex(), input.sex(), true);
        trackChange(changed, entry, "phone", patient.getPhone(), phone, true);
        trackChange(changed, entry, "email", patient.getEmail(), email, true);
        trackChange(changed, entry, "address", patient.getAddress(), address, false);
        trackChange(changed, entry, "emergencyContactName", patient.getEmergencyContactName(), contactName, false);
        trackChange(changed, entry, "emergencyContactPhone", patient.getEmergencyContactPhone(), contactPhone, false);
        trackChange(changed, entry, "insuranceProvider", patient.getInsuranceProvider(), insurer, false);
        trackChange(changed, entry, "insurancePolicyNumber", patient.getInsurancePolicyNumber(), policy, false);
        if (changed.isEmpty()) {
            return PatientView.from(patient);
        }
        patient.updateDemographics(firstName, lastName, input.dateOfBirth(), input.sex(), phone, email, address,
            contactName, contactPhone, insurer, policy);
        // Flush first: a conflicting save fails here, before any audit entry exists, and the view shows the new version.
        patients.saveAndFlush(patient);
        audit.record(entry.detail("changed", String.join(",", changed)));
        return PatientView.from(patient);
    }

    private static void trackChange(List<String> changed, AuditEntry entry, String field, Object before,
                                    Object after, boolean recordValues) {
        if (Objects.equals(before, after)) {
            return;
        }
        changed.add(field);
        if (recordValues) {
            entry.detail(field + ".before", before == null ? "(none)" : before)
                .detail(field + ".after", after == null ? "(none)" : after);
        }
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
