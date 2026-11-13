package com.careconnect.patient.service;

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

@Service
public class PatientChartService {

    private final PatientRepository patients;
    private final AuditService audit;
    private final PatientAccessPolicy policy;

    public PatientChartService(PatientRepository patients, AuditService audit, PatientAccessPolicy policy) {
        this.patients = patients;
        this.audit = audit;
        this.policy = policy;
    }

    @Transactional
    public PatientView open(ChartActor actor, long patientId) {
        policy.require(actor, patientId, ChartScope.DEMOGRAPHICS);
        Patient patient = patients.findById(patientId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found."));
        audit.record(AuditEntry.of(AuditAction.PATIENT_VIEWED, AuditOutcome.SUCCESS)
            .on("PATIENT", patient.getId()).patient(patient.getId()));
        return PatientView.from(patient);
    }
}
