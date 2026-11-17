package com.careconnect.patient.repository;

import java.util.List;

import com.careconnect.patient.domain.Allergy;
import com.careconnect.patient.domain.AllergyStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    List<Allergy> findByPatientId(Long patientId);

    boolean existsByPatientIdAndStatusAndSubstanceIgnoreCase(Long patientId, AllergyStatus status, String substance);
}
