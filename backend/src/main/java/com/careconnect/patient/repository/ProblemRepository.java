package com.careconnect.patient.repository;

import java.util.List;

import com.careconnect.patient.domain.Problem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByPatientId(Long patientId);
}
