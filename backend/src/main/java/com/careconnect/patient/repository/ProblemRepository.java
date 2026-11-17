package com.careconnect.patient.repository;

import java.util.List;

import com.careconnect.patient.domain.Problem;
import com.careconnect.patient.domain.ProblemStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByPatientId(Long patientId);

    boolean existsByPatientIdAndStatusAndIcd10Code(Long patientId, ProblemStatus status, String icd10Code);
}
