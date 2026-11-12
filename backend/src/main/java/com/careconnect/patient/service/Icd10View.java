package com.careconnect.patient.service;

import com.careconnect.patient.domain.Icd10Code;

public record Icd10View(String code, String description) {

    static Icd10View from(Icd10Code c) {
        return new Icd10View(c.getCode(), c.getDescription());
    }
}
