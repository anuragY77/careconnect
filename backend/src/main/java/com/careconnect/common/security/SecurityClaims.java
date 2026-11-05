package com.careconnect.common.security;

/**
 * Names of the access token claims that the security configuration reads. The auth module writes
 * the token with the same names, so the two sides cannot drift apart.
 */
public final class SecurityClaims {

    /** The user's role, for example {@code DOCTOR}. */
    public static final String ROLE = "role";

    /**
     * Put in the {@link #ROLE} claim, instead of the real role, while a user still has to choose a
     * new password. It matches no role rule, so such a token can only reach the endpoints that
     * accept any signed-in user (for example changing the password).
     */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private SecurityClaims() {
    }
}
