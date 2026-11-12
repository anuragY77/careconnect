package com.careconnect.patient.domain;

public enum ProblemStatus {
    ACTIVE,
    RESOLVED,
    ENTERED_IN_ERROR;

    public boolean canChangeTo(ProblemStatus target) {
        return switch (this) {
            case ACTIVE -> target == RESOLVED || target == ENTERED_IN_ERROR;
            case RESOLVED -> target == ACTIVE || target == ENTERED_IN_ERROR;
            case ENTERED_IN_ERROR -> false;
        };
    }
}
