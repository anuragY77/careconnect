package com.careconnect.common.security;

/**
 * Names of the access token claims that the security configuration reads. The auth module writes
 * the token with the same names, so the two sides cannot drift apart.
 */
public final class SecurityClaims {

    /** The user's role, for example {@code DOCTOR}. */
    public static final String ROLE = "role";

    private SecurityClaims() {
    }
}
