package com.careconnect.patient.service;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.audit.service.AuditEntry;
import com.careconnect.audit.service.AuditService;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.Allergy;
import com.careconnect.patient.domain.AllergySeverity;
import com.careconnect.patient.domain.AllergyStatus;
import com.careconnect.patient.repository.AllergyRepository;
import com.careconnect.patient.repository.PatientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allergy list rules (FR-08, BR-08). Allergies are added and given a new status, never deleted. The person
 * who records an allergy comes from the signed-in user, never from the request. Every change is audited in
 * the same transaction. The audit entry holds identifiers, severity and the status before and after; the
 * substance, the reaction and the written reason are clinical text and stay out of it.
 */
@Service
public class AllergyService {

    static final int MAX_REASON_LENGTH = 200;

    private final AllergyRepository allergies;
    private final PatientRepository patients;
    private final AuditService audit;

    public AllergyService(AllergyRepository allergies, PatientRepository patients, AuditService audit) {
        this.allergies = allergies;
        this.patients = patients;
        this.audit = audit;
    }

    @Transactional
    public AllergyView add(long actingUserId, long patientId, String substance, String reaction,
                           AllergySeverity severity) {
        if (!patients.existsById(patientId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found.");
        }
        String cleanSubstance = substance == null ? "" : substance.trim().replaceAll("\\s+", " ");
        if (cleanSubstance.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The substance is required.");
        }
        if (severity == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The severity is required.");
        }
        requireNoActiveCopy(patientId, cleanSubstance);
        Allergy saved = allergies.save(Allergy.record(patientId, cleanSubstance, optional(reaction), severity,
            actingUserId));
        audit.record(AuditEntry.of(AuditAction.ALLERGY_ADDED, AuditOutcome.SUCCESS)
            .on("ALLERGY", saved.getId()).patient(patientId).detail("severity", severity));
        return AllergyView.from(saved);
    }

    /**
     * Marks an allergy inactive or entered in error, or makes an inactive one active again.
     * {@code expectedVersion} is the version the caller saw; another version means someone changed it meanwhile.
     */
    @Transactional
    public AllergyView changeStatus(long allergyId, long expectedVersion, AllergyStatus target, String reason) {
        Allergy allergy = allergies.findById(allergyId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Allergy not found."));
        if (allergy.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "The allergy was changed by someone else. Reload the record and try again.");
        }
        if (target == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The new status is required.");
        }
        AllergyStatus before = allergy.getStatus();
        if (target == before) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "The allergy already has this status.");
        }
        if (!before.canChangeTo(target)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "An allergy that was entered in error cannot be changed again.");
        }
        String cleanReason = optional(reason);
        if (target != AllergyStatus.ACTIVE && cleanReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A reason is required.");
        }
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "The reason can have at most " + MAX_REASON_LENGTH + " characters.");
        }
        if (target == AllergyStatus.ACTIVE) {
            requireNoActiveCopy(allergy.getPatientId(), allergy.getSubstance());
        }
        allergy.changeStatus(target, cleanReason);
        allergies.saveAndFlush(allergy);
        audit.record(AuditEntry.of(AuditAction.ALLERGY_STATUS_CHANGED, AuditOutcome.SUCCESS)
            .on("ALLERGY", allergy.getId()).patient(allergy.getPatientId())
            .detail("severity", allergy.getSeverity())
            .detail("statusBefore", before).detail("statusAfter", target));
        return AllergyView.from(allergy);
    }

    private void requireNoActiveCopy(long patientId, String substance) {
        if (allergies.existsByPatientIdAndStatusAndSubstanceIgnoreCase(patientId, AllergyStatus.ACTIVE, substance)) {
            throw new ApiException(ErrorCode.CONFLICT, "This allergy is already on the patient's active list.");
        }
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }
}
