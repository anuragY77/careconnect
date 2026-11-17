package com.careconnect.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.JwtService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PatientAuthorizationMatrixIntegrationTest {

    private static final Set<Role> FRONT_DESK_AND_CLINICIANS = Set.of(Role.RECEPTIONIST, Role.DOCTOR, Role.NURSE);

    private static final Set<Role> CLINICIANS = Set.of(Role.DOCTOR, Role.NURSE);

    private static final String UNKNOWN_ID = "999999999";

    private static final String NEW_PATIENT = "{\"firstName\":\"Matrix\",\"lastName\":\"Mx"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\"}";

    private static final String UPDATE_PATIENT =
        "{\"version\":0,\"firstName\":\"Matrix\",\"lastName\":\"Mx\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\"}";

    private static final String NEW_ALLERGY = "{\"substance\":\"Latex\",\"severity\":\"MILD\"}";

    private static final String NEW_PROBLEM = "{\"icd10Code\":\"I10\"}";

    private static final String NEW_STATUS = "{\"version\":0,\"status\":\"INACTIVE\",\"reason\":\"Matrix\"}";

    private static final String NEW_PROBLEM_STATUS = "{\"version\":0,\"status\":\"RESOLVED\"}";

    private record Route(HttpMethod method, String pattern, String path, String body, Set<Role> allowed) {

        String key() {
            return method.name() + " " + pattern;
        }

        MockHttpServletRequestBuilder builder() {
            MockHttpServletRequestBuilder builder = request(method, path);
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return builder;
        }
    }

    private static final List<Route> ROUTES = List.of(
        new Route(HttpMethod.GET, "/api/patients", "/api/patients?q=zz", null, FRONT_DESK_AND_CLINICIANS),
        new Route(HttpMethod.POST, "/api/patients", "/api/patients", NEW_PATIENT, FRONT_DESK_AND_CLINICIANS),
        new Route(HttpMethod.PUT, "/api/patients/{id}", "/api/patients/" + UNKNOWN_ID, UPDATE_PATIENT,
            FRONT_DESK_AND_CLINICIANS),
        new Route(HttpMethod.GET, "/api/patients/{patientId}", "/api/patients/" + UNKNOWN_ID, null,
            FRONT_DESK_AND_CLINICIANS),
        new Route(HttpMethod.GET, "/api/patients/{patientId}/summary", "/api/patients/" + UNKNOWN_ID + "/summary", null,
            CLINICIANS),
        new Route(HttpMethod.POST, "/api/patients/{patientId}/allergies", "/api/patients/" + UNKNOWN_ID + "/allergies",
            NEW_ALLERGY, CLINICIANS),
        new Route(HttpMethod.PATCH, "/api/allergies/{allergyId}/status", "/api/allergies/" + UNKNOWN_ID + "/status",
            NEW_STATUS, CLINICIANS),
        new Route(HttpMethod.POST, "/api/patients/{patientId}/problems", "/api/patients/" + UNKNOWN_ID + "/problems",
            NEW_PROBLEM, CLINICIANS),
        new Route(HttpMethod.PATCH, "/api/problems/{problemId}/status", "/api/problems/" + UNKNOWN_ID + "/status",
            NEW_PROBLEM_STATUS, CLINICIANS),
        new Route(HttpMethod.GET, "/api/icd10", "/api/icd10?q=diabetes", null, CLINICIANS));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String token(Role role, boolean mustChangePassword) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(1L, "user", "User", role, mustChangePassword)).value();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleGetsExactlyTheAnswerTheRulesSay(Role role) throws Exception {
        for (Route route : ROUTES) {
            var result = mvc.perform(route.builder().header("Authorization", token(role, false))).andReturn();
            int status = result.getResponse().getStatus();
            if (route.allowed().contains(role)) {
                assertThat(status).as(role + " " + route.key()).isNotIn(401, 403).isLessThan(500);
            } else {
                assertThat(status).as(role + " " + route.key()).isEqualTo(403);
                assertThat(result.getResponse().getContentAsString()).as(role + " " + route.key())
                    .contains("\"code\":\"ACCESS_DENIED\"");
            }
        }
    }

    @Test
    void withoutATokenEveryEndpointIs401() throws Exception {
        for (Route route : ROUTES) {
            mvc.perform(route.builder())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void aTokenThatStillNeedsAPasswordChangeIsRefusedEverywhere() throws Exception {
        for (Role role : Role.values()) {
            for (Route route : ROUTES) {
                mvc.perform(route.builder().header("Authorization", token(role, true)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
        }
    }

    @Test
    void theTableCoversEveryEndpointOfThePatientModuleAndNothingElse() {
        Set<String> actual = handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> "com.careconnect.patient.api".equals(
                ClassUtils.getUserClass(entry.getValue().getBeanType()).getPackageName()))
            .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                .flatMap(method -> entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                    .map(pattern -> method.name() + " " + pattern)))
            .collect(Collectors.toSet());

        Set<String> covered = ROUTES.stream().map(Route::key).collect(Collectors.toSet());

        assertThat(covered).isEqualTo(actual);
    }
}
