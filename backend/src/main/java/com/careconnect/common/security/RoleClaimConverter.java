package com.careconnect.common.security;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns the {@code role} claim of a verified access token into one authority, {@code ROLE_DOCTOR}
 * for example. A missing claim or a value that does not look like a role name gives no authority,
 * so such a token can reach "any authenticated user" endpoints but no role-restricted one.
 */
final class RoleClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Pattern ROLE_NAME = Pattern.compile("[A-Z_]{1,30}");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object claim = jwt.getClaim(SecurityClaims.ROLE);
        if (claim instanceof String role && ROLE_NAME.matcher(role).matches()) {
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return List.of();
    }
}
