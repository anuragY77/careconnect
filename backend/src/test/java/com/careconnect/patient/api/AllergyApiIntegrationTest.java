package com.careconnect.patient.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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

/** Allergy endpoints through the full security chain (TC-15, TC-16). */
// Same settings as the other API tests, so all share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AllergyApiIntegrationTest {

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
        String body = "{\"firstName\":\"Asha\",\"lastName\":\"Al" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
            + "\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\"}";
        String created = mvc.perform(post("/api/patients").header("Authorization", login(Role.RECEPTIONIST).token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.patientId")).longValue();
    }

    private ResultActions add(String token, long patientId, String body) throws Exception {
        var request = post("/api/patients/" + patientId + "/allergies").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private ResultActions change(String token, long allergyId, String body) throws Exception {
        var request = patch("/api/allergies/" + allergyId + "/status").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private static String allergy(String substance) {
        return "{\"substance\":\"" + substance + "\",\"reaction\":\"Rash\",\"severity\":\"SEVERE\"}";
    }

    private static String status(long version, String status, String reason) {
        return "{\"version\":" + version + ",\"status\":\"" + status + "\""
            + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    @Test
    void doctorsAndNursesAddAnAllergyAndAreRecordedAsWhoAddedIt() throws Exception {
        long patient = newPatient();

        for (Role role : new Role[] {Role.DOCTOR, Role.NURSE}) {
            Login who = login(role);
            add(who.token(), patient, allergy("Substance-" + role.name()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/allergies/\\d+")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.recordedBy").value(who.userId()))
                .andExpect(jsonPath("$.patientId").value(patient))
                .andExpect(jsonPath("$.version").value(0));
        }
    }

    @Test
    void whoRecordedItAndTheStatusCanNotBeChosenByTheCaller() throws Exception {
        Login doctor = login(Role.DOCTOR);
        String body = allergy("Chosen-" + UUID.randomUUID().toString().substring(0, 8))
            .replace("{", "{\"recordedBy\":1,\"status\":\"INACTIVE\",");

        var response = add(doctor.token(), newPatient(), body).andReturn().getResponse();

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isIn(201, 400);
        if (response.getStatus() == 201) {
            org.assertj.core.api.Assertions.assertThat(((Number) JsonPath.read(response.getContentAsString(), "$.recordedBy")).longValue())
                .isEqualTo(doctor.userId());
            org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(response.getContentAsString(), "$.status")).isEqualTo("ACTIVE");
        }
    }

    @Test
    void otherRolesAndAnonymousCallersAreRefusedOnBothEndpoints() throws Exception {
        long patient = newPatient();
        long allergyId = ((Number) JsonPath.read(add(login(Role.DOCTOR).token(), patient, allergy("Guarded"))
            .andReturn().getResponse().getContentAsString(), "$.allergyId")).longValue();

        for (Role role : new Role[] {Role.RECEPTIONIST, Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            String token = login(role).token();
            add(token, patient, allergy("Other-" + role.name())).andExpect(status().isForbidden());
            change(token, allergyId, status(0, "INACTIVE", "reason")).andExpect(status().isForbidden());
        }
        add(null, patient, allergy("Anon")).andExpect(status().isUnauthorized());
        change(null, allergyId, status(0, "INACTIVE", "reason")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputAndAnUnknownPatientAreRefused() throws Exception {
        String token = login(Role.DOCTOR).token();
        long patient = newPatient();

        add(token, patient, "{\"substance\":\" \",\"severity\":\"MILD\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("substance")));
        add(token, patient, "{\"substance\":\"Latex\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("severity")));
        add(token, patient, "{\"substance\":\"Latex\",\"severity\":\"EXTREME\"}").andExpect(status().isBadRequest());
        add(token, 999_999_999L, allergy("Latex")).andExpect(status().isNotFound());
    }

    @Test
    void theSameActiveAllergyTwiceIsAConflict() throws Exception {
        String token = login(Role.NURSE).token();
        long patient = newPatient();
        add(token, patient, allergy("Latex")).andExpect(status().isCreated());

        add(token, patient, allergy("LATEX")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void theStatusChangesWithAReasonAndTheVersionCountsUp() throws Exception {
        String token = login(Role.DOCTOR).token();
        String created = add(token, newPatient(), allergy("Penicillin")).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.allergyId")).longValue();

        change(token, id, status(0, "INACTIVE", null))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        change(token, id, status(0, "INACTIVE", "Outgrown"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.statusReason").value("Outgrown"))
            .andExpect(jsonPath("$.version").value(1));
        change(token, id, status(0, "ENTERED_IN_ERROR", "Wrong patient"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
        change(token, id, status(1, "INACTIVE", "Again"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        change(token, id, status(1, "ENTERED_IN_ERROR", "Wrong patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        change(token, id, status(2, "ACTIVE", null))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void anUnknownAllergyAndAMissingVersionAreRefused() throws Exception {
        String token = login(Role.DOCTOR).token();

        change(token, 999_999_999L, status(0, "INACTIVE", "x")).andExpect(status().isNotFound());
        change(token, 1, "{\"status\":\"INACTIVE\",\"reason\":\"x\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field").value(hasItem("version")));
    }
}
