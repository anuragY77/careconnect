package com.careconnect.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Set;

import com.careconnect.auth.domain.Role;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.junit.jupiter.api.Test;

class PatientAccessPolicyTest {

    private final PatientAccessPolicy policy = new PatientAccessPolicy();

    @Test
    void scopesAreOrderedFromNothingToFull() {
        assertThat(ChartScope.FULL.covers(ChartScope.DEMOGRAPHICS)).isTrue();
        assertThat(ChartScope.FULL.covers(ChartScope.FULL)).isTrue();
        assertThat(ChartScope.DEMOGRAPHICS.covers(ChartScope.DEMOGRAPHICS)).isTrue();
        assertThat(ChartScope.DEMOGRAPHICS.covers(ChartScope.FULL)).isFalse();
        assertThat(ChartScope.NONE.covers(ChartScope.DEMOGRAPHICS)).isFalse();
    }

    @Test
    void doctorsAndNursesSeeTheWholeChartOfAnyPatient() {
        for (String role : new String[] {"DOCTOR", "NURSE"}) {
            assertThat(policy.scopeFor(new ChartActor(role, null), 5L)).isEqualTo(ChartScope.FULL);
        }
    }

    @Test
    void theFrontDeskSeesDemographicsOnly() {
        ChartActor receptionist = new ChartActor("RECEPTIONIST", null);

        assertThat(policy.scopeFor(receptionist, 5L)).isEqualTo(ChartScope.DEMOGRAPHICS);
        assertThat(catchThrowable(() -> policy.require(receptionist, 5L, ChartScope.FULL)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatCode(() -> policy.require(receptionist, 5L, ChartScope.DEMOGRAPHICS)).doesNotThrowAnyException();
    }

    @Test
    void aPatientSeesOnlyTheRecordNamedInTheirToken() {
        assertThat(policy.scopeFor(new ChartActor("PATIENT", 5L), 5L)).isEqualTo(ChartScope.FULL);
        assertThat(policy.scopeFor(new ChartActor("PATIENT", 5L), 6L)).isEqualTo(ChartScope.NONE);
        assertThat(policy.scopeFor(new ChartActor("PATIENT", null), 5L)).isEqualTo(ChartScope.NONE);
    }

    @Test
    void everyOtherRoleAndAnUnknownCallerSeesNothing() {
        Set<Role> allowed = Set.of(Role.DOCTOR, Role.NURSE, Role.RECEPTIONIST, Role.PATIENT);
        for (Role role : Role.values()) {
            if (!allowed.contains(role)) {
                assertThat(policy.scopeFor(new ChartActor(role.name(), 5L), 5L)).as(role.name()).isEqualTo(ChartScope.NONE);
            }
        }
        assertThat(policy.scopeFor(new ChartActor("PASSWORD_CHANGE_REQUIRED", 5L), 5L)).isEqualTo(ChartScope.NONE);
        assertThat(policy.scopeFor(new ChartActor("SOMETHING_NEW", null), 5L)).isEqualTo(ChartScope.NONE);
        assertThat(policy.scopeFor(new ChartActor(null, 5L), 5L)).isEqualTo(ChartScope.NONE);
        assertThat(policy.scopeFor(null, 5L)).isEqualTo(ChartScope.NONE);
    }
}
