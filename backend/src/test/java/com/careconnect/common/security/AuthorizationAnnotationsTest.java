package com.careconnect.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import com.careconnect.auth.domain.Role;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

/** Keeps the role annotations and the {@code Role} enum in step, because common cannot import the enum. */
class AuthorizationAnnotationsTest {

    private static String ruleOf(Class<? extends Annotation> annotation) {
        PreAuthorize rule = annotation.getAnnotation(PreAuthorize.class);
        assertThat(rule).as(annotation.getSimpleName() + " carries @PreAuthorize").isNotNull();
        return rule.value();
    }

    @Test
    void staffOnlyNamesEveryStaffRoleAndNeverPatient() {
        String rule = ruleOf(StaffOnly.class);

        for (Role role : Role.values()) {
            assertThat(rule.contains("'" + role.name() + "'"))
                .as("StaffOnly mentions " + role)
                .isEqualTo(role.isStaff());
        }
    }

    @Test
    void adminOnlyAndPatientOnlyNameExactlyOneRole() {
        assertThat(ruleOf(AdminOnly.class)).isEqualTo("hasRole('" + Role.ADMIN.name() + "')");
        assertThat(ruleOf(PatientOnly.class)).isEqualTo("hasRole('" + Role.PATIENT.name() + "')");
    }

    @Test
    void anyAuthenticatedUserOnlyRequiresASignIn() {
        assertThat(ruleOf(AnyAuthenticatedUser.class)).isEqualTo("isAuthenticated()");
    }

    @Test
    void everyRoleNameSurvivesTheTokenConversion() {
        RoleClaimConverter converter = new RoleClaimConverter();

        for (Role role : Role.values()) {
            Jwt token = Jwt.withTokenValue("t").header("alg", "HS256").claim(SecurityClaims.ROLE, role.name()).build();

            assertThat(converter.convert(token)).extracting(Object::toString)
                .as("authority for " + role)
                .containsExactly("ROLE_" + role.name());
        }
    }
}
