package com.careconnect.patient.api;

/** Format rules shared by the patient request bodies. */
final class PatientFieldRules {

    /** Empty, or 7 to 20 digits, spaces, + ( ) and -. */
    static final String PHONE = "|[0-9+()\\- ]{7,20}";

    static final String PHONE_MESSAGE = "must be a phone number of 7 to 20 digits, spaces, + ( ) or -";

    private PatientFieldRules() {
    }
}
