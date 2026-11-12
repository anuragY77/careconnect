package com.careconnect.patient.domain;

/**
 * An allergy is never deleted (BR-08). It is ACTIVE, INACTIVE (no longer applies, for example outgrown) or
 * ENTERED_IN_ERROR (recorded by mistake). A row entered in error stays visible and is final.
 */
public enum AllergyStatus {
    ACTIVE,
    INACTIVE,
    ENTERED_IN_ERROR;

    public boolean canChangeTo(AllergyStatus target) {
        return switch (this) {
            case ACTIVE -> target == INACTIVE || target == ENTERED_IN_ERROR;
            case INACTIVE -> target == ACTIVE || target == ENTERED_IN_ERROR;
            case ENTERED_IN_ERROR -> false;
        };
    }
}
