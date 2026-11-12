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
import com.careconnect.patient.domain.Icd10Code;
import com.careconnect.patient.domain.Problem;
import com.careconnect.patient.domain.ProblemStatus;
import com.careconnect.patient.repository.Icd10CodeRepository;
import com.careconnect.patient.repository.PatientRepository;
import com.careconnect.patient.repository.ProblemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

    static final int MAX_REASON_LENGTH = 200;

    private final ProblemRepository problems;
    private final Icd10CodeRepository codes;
    private final PatientRepository patients;
    private final AuditService audit;
    private final Clock clock;

    public ProblemService(ProblemRepository problems, Icd10CodeRepository codes, PatientRepository patients,
                          AuditService audit, Clock clock) {
        this.problems = problems;
        this.codes = codes;
        this.patients = patients;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ProblemView add(long actingUserId, long patientId, String icd10Code, LocalDate onsetDate) {
        if (!patients.existsById(patientId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found.");
        }
        String code = icd10Code == null ? "" : icd10Code.trim().toUpperCase(Locale.ROOT);
        Icd10Code entry = codes.findById(code).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
            "Unknown diagnosis code. Choose a code from the list."));
        requireValidOnsetDate(onsetDate);
        if (problems.existsByPatientIdAndStatusAndIcd10Code(patientId, ProblemStatus.ACTIVE, entry.getCode())) {
            throw new ApiException(ErrorCode.CONFLICT, "This problem is already on the patient's active list.");
        }
        Problem saved = problems.save(Problem.record(patientId, entry.getCode(), entry.getDescription(), onsetDate,
            actingUserId));
        audit.record(AuditEntry.of(AuditAction.PROBLEM_ADDED, AuditOutcome.SUCCESS)
            .on("PROBLEM", saved.getId()).patient(patientId));
        return ProblemView.from(saved);
    }

    @Transactional
    public ProblemView changeStatus(long problemId, long expectedVersion, ProblemStatus target, String reason) {
        Problem problem = problems.findById(problemId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Problem not found."));
        if (problem.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "The problem was changed by someone else. Reload the record and try again.");
        }
        if (target == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The new status is required.");
        }
        ProblemStatus before = problem.getStatus();
        if (target == before) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "The problem already has this status.");
        }
        if (!before.canChangeTo(target)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "A problem that was entered in error cannot be changed again.");
        }
        String cleanReason = optional(reason);
        if (target == ProblemStatus.ENTERED_IN_ERROR && cleanReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A reason is required.");
        }
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "The reason can have at most " + MAX_REASON_LENGTH + " characters.");
        }
        if (target == ProblemStatus.ACTIVE
            && problems.existsByPatientIdAndStatusAndIcd10Code(problem.getPatientId(), ProblemStatus.ACTIVE,
                problem.getIcd10Code())) {
            throw new ApiException(ErrorCode.CONFLICT, "This problem is already on the patient's active list.");
        }
        problem.changeStatus(target, cleanReason);
        problems.saveAndFlush(problem);
        audit.record(AuditEntry.of(AuditAction.PROBLEM_STATUS_CHANGED, AuditOutcome.SUCCESS)
            .on("PROBLEM", problem.getId()).patient(problem.getPatientId())
            .detail("statusBefore", before).detail("statusAfter", target));
        return ProblemView.from(problem);
    }

    private void requireValidOnsetDate(LocalDate onsetDate) {
        if (onsetDate == null) {
            return;
        }
        LocalDate latest = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
        if (onsetDate.isAfter(latest)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The onset date cannot be in the future.");
        }
        if (onsetDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The onset date cannot be before 1900.");
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
