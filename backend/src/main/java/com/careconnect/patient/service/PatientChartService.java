package com.careconnect.patient.service;

import java.util.Comparator;
import java.util.List;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.audit.service.AuditEntry;
import com.careconnect.audit.service.AuditService;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.domain.Allergy;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.Problem;
import com.careconnect.patient.repository.AllergyRepository;
import com.careconnect.patient.repository.PatientRepository;
import com.careconnect.patient.repository.ProblemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientChartService {

    private static final Comparator<Allergy> ALLERGY_ORDER = Comparator
        .comparingInt((Allergy a) -> a.getStatus().ordinal())
        .thenComparing(a -> a.getSeverity().ordinal(), Comparator.reverseOrder())
        .thenComparing(Allergy::getRecordedAt, Comparator.reverseOrder())
        .thenComparing(Allergy::getId, Comparator.reverseOrder());

    private static final Comparator<Problem> PROBLEM_ORDER = Comparator
        .comparingInt((Problem p) -> p.getStatus().ordinal())
        .thenComparing(Problem::getRecordedAt, Comparator.reverseOrder())
        .thenComparing(Problem::getId, Comparator.reverseOrder());

    private final PatientRepository patients;
    private final AllergyRepository allergies;
    private final ProblemRepository problems;
    private final AuditService audit;
    private final PatientAccessPolicy policy;

    public PatientChartService(PatientRepository patients, AllergyRepository allergies, ProblemRepository problems,
                               AuditService audit, PatientAccessPolicy policy) {
        this.patients = patients;
        this.allergies = allergies;
        this.problems = problems;
        this.audit = audit;
        this.policy = policy;
    }

    @Transactional
    public PatientView open(ChartActor actor, long patientId) {
        policy.require(actor, patientId, ChartScope.DEMOGRAPHICS);
        Patient patient = find(patientId);
        audit.record(AuditEntry.of(AuditAction.PATIENT_VIEWED, AuditOutcome.SUCCESS)
            .on("PATIENT", patient.getId()).patient(patient.getId()));
        return PatientView.from(patient);
    }

    @Transactional
    public ChartSummary summary(ChartActor actor, long patientId) {
        policy.require(actor, patientId, ChartScope.FULL);
        Patient patient = find(patientId);
        List<AllergyView> allergyViews = allergies.findByPatientId(patientId).stream()
            .sorted(ALLERGY_ORDER).map(AllergyView::from).toList();
        List<ProblemView> problemViews = problems.findByPatientId(patientId).stream()
            .sorted(PROBLEM_ORDER).map(ProblemView::from).toList();
        audit.record(AuditEntry.of(AuditAction.CHART_VIEWED, AuditOutcome.SUCCESS)
            .on("PATIENT", patient.getId()).patient(patient.getId())
            .detail("sections", "demographics,allergies,problems"));
        return new ChartSummary(PatientView.from(patient), allergyViews, problemViews);
    }

    private Patient find(long patientId) {
        return patients.findById(patientId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found."));
    }
}
