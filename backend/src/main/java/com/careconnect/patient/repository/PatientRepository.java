package com.careconnect.patient.repository;

import com.careconnect.patient.domain.Patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence for patients. Only the patient module's services use it (ArchUnit enforces this). */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** Next number of the MRN sequence. Numbers are never reused, so a rolled-back registration leaves a gap. */
    @Query(value = "select nextval('patient_mrn_seq')", nativeQuery = true)
    long nextMrnNumber();
}
