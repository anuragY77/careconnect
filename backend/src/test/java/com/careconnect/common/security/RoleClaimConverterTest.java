package com.careconnect.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RoleClaimConverterTest {

    private final RoleClaimConverter converter = new RoleClaimConverter();

    private static Jwt tokenWith(String claim, Object value) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256").subject("1");
        if (claim != null) {
            builder.claim(claim, value);
        }
        return builder.build();
    }

    @Test
    void roleClaimBecomesOneRoleAuthority() {
        assertThat(converter.convert(tokenWith("role", "DOCTOR")))
            .extracting(Object::toString).containsExactly("ROLE_DOCTOR");
        assertThat(converter.convert(tokenWith("role", "LAB_TECHNICIAN")))
            .extracting(Object::toString).containsExactly("ROLE_LAB_TECHNICIAN");
    }

    @Test
    void missingClaimGivesNoAuthority() {
        assertThat(converter.convert(tokenWith(null, null))).isEmpty();
    }

    @Test
    void valuesThatAreNotRoleNamesGiveNoAuthority() {
        assertThat(converter.convert(tokenWith("role", ""))).isEmpty();
        assertThat(converter.convert(tokenWith("role", "doctor"))).isEmpty();
        assertThat(converter.convert(tokenWith("role", "ADMIN,DOCTOR"))).isEmpty();
        assertThat(converter.convert(tokenWith("role", "ROLE_ADMIN OR 1=1"))).isEmpty();
        assertThat(converter.convert(tokenWith("role", "A".repeat(31)))).isEmpty();
        assertThat(converter.convert(tokenWith("role", 42))).isEmpty();
        assertThat(converter.convert(tokenWith("role", List.of("ADMIN")))).isEmpty();
    }

    @Test
    void claimNameIsTheOneTheTokenIsWrittenWith() {
        assertThat(SecurityClaims.ROLE).isEqualTo("role");
    }
}
