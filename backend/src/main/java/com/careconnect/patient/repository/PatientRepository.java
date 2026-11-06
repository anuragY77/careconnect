package com.careconnect.patient.repository;

import java.time.LocalDate;
import java.util.List;

import com.careconnect.patient.domain.Patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for patients. Only the patient module's services use it (ArchUnit enforces this). */
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    /** Next number of the MRN sequence. Numbers are never reused, so a rolled-back registration leaves a gap. */
    @Query(value = "select nextval('patient_mrn_seq')", nativeQuery = true)
    long nextMrnNumber();

    /** Same date of birth and the same name, ignoring letter case. */
    List<Patient> findTop5ByDateOfBirthAndFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderById(
        LocalDate dateOfBirth, String firstName, String lastName);

    /** Same date of birth and the same phone number, comparing digits only (spaces and dashes ignored). */
    @Query("select p from Patient p where p.dateOfBirth = :dob "
        + "and function('regexp_replace', p.phone, '[^0-9]', '', 'g') = :digits order by p.id")
    List<Patient> findByDateOfBirthAndPhoneDigits(@Param("dob") LocalDate dob, @Param("digits") String digits);
}
