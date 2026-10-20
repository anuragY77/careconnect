package com.careconnect.auth.domain;

/** The seven roles of the system (PRD section 2.3). Stored by name; never rename a constant. */
public enum Role {
    PATIENT,
    DOCTOR,
    NURSE,
    PHARMACIST,
    RECEPTIONIST,
    LAB_TECHNICIAN,
    ADMIN;

    /** True for every role except PATIENT. */
    public boolean isStaff() {
        return this != PATIENT;
    }
}
