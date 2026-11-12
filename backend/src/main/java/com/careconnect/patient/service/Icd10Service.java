package com.careconnect.patient.service;

import java.util.List;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.patient.repository.Icd10CodeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Icd10Service {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 60;

    private final Icd10CodeRepository codes;

    public Icd10Service(Icd10CodeRepository codes) {
        this.codes = codes;
    }

    @Transactional(readOnly = true)
    public List<Icd10View> search(String query) {
        String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (q.length() < MIN_QUERY_LENGTH || q.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "The search text must have " + MIN_QUERY_LENGTH + " to " + MAX_QUERY_LENGTH + " characters.");
        }
        return codes.findTop20ByCodeStartingWithIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByCode(q, q)
            .stream().map(Icd10View::from).toList();
    }
}
