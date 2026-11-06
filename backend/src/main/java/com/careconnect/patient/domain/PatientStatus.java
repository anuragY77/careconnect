package com.careconnect.patient.domain;

/** A patient is never deleted: a record that is no longer in use becomes INACTIVE. */
public enum PatientStatus {
    ACTIVE,
    INACTIVE
}
