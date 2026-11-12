package com.careconnect.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "icd10_code")
public class Icd10Code {

    @Id
    @Column(name = "code", length = 10, updatable = false)
    private String code;

    @Column(name = "description", nullable = false, updatable = false, length = 200)
    private String description;

    protected Icd10Code() {
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
