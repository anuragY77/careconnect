package com.careconnect.patient.repository;

import com.careconnect.patient.domain.Problem;
import com.careconnect.patient.domain.ProblemStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    boolean existsByPatientIdAndStatusAndIcd10Code(Long patientId, ProblemStatus status, String icd10Code);
}
