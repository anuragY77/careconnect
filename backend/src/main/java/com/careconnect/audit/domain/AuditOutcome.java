package com.careconnect.audit.domain;

/** How the audited action ended. Stored by name; never rename a constant. */
public enum AuditOutcome {
    SUCCESS,
    /** The user was not allowed to do it. */
    DENIED,
    /** It was allowed but did not work (for example a wrong password). */
    FAILURE
}
