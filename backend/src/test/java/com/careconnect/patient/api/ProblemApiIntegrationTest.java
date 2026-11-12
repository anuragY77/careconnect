package com.careconnect.patient.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProblemApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    private record Login(String token, long userId) { }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private Login login(Role role) throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser user = users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name, name + "@example.org", role, null, false));
        String json = "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}";
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Login("Bearer " + JsonPath.read(body, "$.accessToken"), user.getId());
    }

    private long newPatient() throws Exception {
        String body = "{\"firstName\":\"Asha\",\"lastName\":\"Pr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
            + "\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\"}";
        String created = mvc.perform(post("/api/patients").header("Authorization", login(Role.RECEPTIONIST).token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.patientId")).longValue();
    }

    private ResultActions add(String token, long patientId, String body) throws Exception {
        var request = post("/api/patients/" + patientId + "/problems").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private ResultActions change(String token, long problemId, String body) throws Exception {
        var request = patch("/api/problems/" + problemId + "/status").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private ResultActions lookup(String token, String q) throws Exception {
        var request = get("/api/icd10").param("q", q);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private static String problem(String code) {
        return "{\"icd10Code\":\"" + code + "\",\"onsetDate\":\"2020-03-01\"}";
    }

    private static String statusBody(long version, String status, String reason) {
        return "{\"version\":" + version + ",\"status\":\"" + status + "\""
            + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    @Test
    void doctorsAndNursesAddAProblemAndAreRecordedAsWhoAddedIt() throws Exception {
        long patient = newPatient();
        String[] codes = {"E11.9", "I10"};
        Role[] roles = {Role.DOCTOR, Role.NURSE};

        for (int i = 0; i < roles.length; i++) {
            Login who = login(roles[i]);
            add(who.token(), patient, problem(codes[i]))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/problems/\\d+")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.icd10Code").value(codes[i]))
                .andExpect(jsonPath("$.onsetDate").value("2020-03-01"))
                .andExpect(jsonPath("$.recordedBy").value(who.userId()))
                .andExpect(jsonPath("$.patientId").value(patient))
                .andExpect(jsonPath("$.version").value(0));
        }
    }

    @Test
    void otherRolesAndAnonymousCallersAreRefusedOnBothEndpoints() throws Exception {
        long patient = newPatient();
        long problemId = ((Number) JsonPath.read(add(login(Role.DOCTOR).token(), patient, problem("K21.9"))
            .andReturn().getResponse().getContentAsString(), "$.problemId")).longValue();

        for (Role role : new Role[] {Role.RECEPTIONIST, Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            String token = login(role).token();
            add(token, patient, problem("J06.9")).andExpect(status().isForbidden());
            change(token, problemId, statusBody(0, "RESOLVED", null)).andExpect(status().isForbidden());
        }
        add(null, patient, problem("J06.9")).andExpect(status().isUnauthorized());
        change(null, problemId, statusBody(0, "RESOLVED", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputAndAnUnknownPatientAreRefused() throws Exception {
        String token = login(Role.DOCTOR).token();
        long patient = newPatient();

        add(token, patient, "{\"icd10Code\":\" \"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("icd10Code")));
        add(token, patient, "{\"icd10Code\":\"Z99.9\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        add(token, patient, "{\"icd10Code\":\"I10\",\"onsetDate\":\"not-a-date\"}").andExpect(status().isBadRequest());
        add(token, patient, "{\"icd10Code\":\"I10\",\"onsetDate\":\"2999-01-01\"}").andExpect(status().isBadRequest());
        add(token, 999_999_999L, problem("I10")).andExpect(status().isNotFound());
    }

    @Test
    void theSameActiveProblemTwiceIsAConflict() throws Exception {
        String token = login(Role.NURSE).token();
        long patient = newPatient();
        add(token, patient, problem("N39.0")).andExpect(status().isCreated());

        add(token, patient, problem("N39.0")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void aProblemIsResolvedAndTheVersionCountsUp() throws Exception {
        String token = login(Role.DOCTOR).token();
        String created = add(token, newPatient(), problem("J18.9")).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.problemId")).longValue();

        change(token, id, statusBody(0, "RESOLVED", null))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.version").value(1));
        change(token, id, statusBody(0, "ENTERED_IN_ERROR", "Wrong patient"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
        change(token, id, statusBody(1, "RESOLVED", null))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        change(token, id, statusBody(1, "ENTERED_IN_ERROR", null))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        change(token, id, statusBody(1, "ENTERED_IN_ERROR", "Wrong patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statusReason").value("Wrong patient"))
            .andExpect(jsonPath("$.version").value(2));
        change(token, id, statusBody(2, "ACTIVE", null))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void anUnknownProblemAndAMissingVersionAreRefused() throws Exception {
        String token = login(Role.DOCTOR).token();

        change(token, 999_999_999L, statusBody(0, "RESOLVED", null)).andExpect(status().isNotFound());
        change(token, 1, "{\"status\":\"RESOLVED\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("version")));
    }

    @Test
    void theCodeLookupIsOpenToDoctorsAndNursesOnly() throws Exception {
        for (Role role : new Role[] {Role.DOCTOR, Role.NURSE}) {
            lookup(login(role).token(), "diabetes").andExpect(status().isOk());
        }
        for (Role role : new Role[] {Role.RECEPTIONIST, Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            lookup(login(role).token(), "diabetes").andExpect(status().isForbidden());
        }
        lookup(null, "diabetes").andExpect(status().isUnauthorized());
    }

    @Test
    void theCodeLookupReturnsMatchesAndRefusesTooShortText() throws Exception {
        String token = login(Role.DOCTOR).token();

        lookup(token, "e11").andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code").value(hasItem("E11.9")))
            .andExpect(jsonPath("$[0].description").isNotEmpty());
        lookup(token, "hypertens").andExpect(jsonPath("$[*].code").value(hasItem("I10")));
        lookup(token, "zzzzzz").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        lookup(token, "a").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
