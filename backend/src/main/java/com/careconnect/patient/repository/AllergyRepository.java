package com.careconnect.patient.repository;

import com.careconnect.patient.domain.Allergy;
import com.careconnect.patient.domain.AllergyStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for allergies. Only the patient module's services use it (ArchUnit enforces this). */
public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    boolean existsByPatientIdAndStatusAndSubstanceIgnoreCase(Long patientId, AllergyStatus status, String substance);
}
