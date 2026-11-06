package com.careconnect.patient.service;

import com.careconnect.patient.repository.PatientRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds patients by name, MRN, phone number or date of birth (FR-06). An empty search is refused, so
 * the whole register cannot be listed. The search text is not written to the audit trail because it
 * usually contains a patient's name; opening a chart is what gets audited.
 */
@Service
public class PatientSearchService {

    public static final int MAX_PAGE_SIZE = 50;

    private static final Sort ORDER = Sort.by("lastName", "firstName", "id");

    private final PatientRepository patients;

    public PatientSearchService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public Page<PatientSummary> search(String text, int page, int size) {
        PatientSearchQuery query = PatientSearchQuery.parse(text);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), ORDER);
        return patients.findAll(PatientSearchSpecifications.of(query), pageable).map(PatientSummary::from);
    }
}
