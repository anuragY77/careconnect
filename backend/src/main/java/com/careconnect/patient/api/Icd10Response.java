package com.careconnect.patient.api;

import com.careconnect.patient.service.Icd10View;

public record Icd10Response(String code, String description) {

    static Icd10Response from(Icd10View v) {
        return new Icd10Response(v.code(), v.description());
    }
}
