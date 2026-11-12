package com.careconnect.patient.repository;

import java.util.List;

import com.careconnect.patient.domain.Icd10Code;

import org.springframework.data.jpa.repository.JpaRepository;

public interface Icd10CodeRepository extends JpaRepository<Icd10Code, String> {

    List<Icd10Code> findTop20ByCodeStartingWithIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByCode(
        String codePrefix, String descriptionPart);
}
