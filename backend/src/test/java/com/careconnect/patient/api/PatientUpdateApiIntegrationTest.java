package com.careconnect.patient.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Demographics update through the full security chain (TC-14). */
// Same settings as the other API tests, so all share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PatientUpdateApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private String tokenFor(Role role) throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name, name + "@example.org", role, null, false));
        String json = "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}";
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    /** Registers a new person and returns the response body. */
    private String registerPatient(String lastName) throws Exception {
        String body = "{\"firstName\":\"Asha\",\"lastName\":\"" + lastName + "\",\"dateOfBirth\":\"1990-05-17\","
            + "\"sex\":\"FEMALE\",\"phone\":\"98765 43210\",\"confirmNotDuplicate\":true}";
        return mvc.perform(post("/api/patients").header("Authorization", tokenFor(Role.RECEPTIONIST))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static String edit(String lastName, long version, String phone) {
        return "{\"version\":" + version + ",\"firstName\":\"Asha\",\"lastName\":\"" + lastName
            + "\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\",\"phone\":\"" + phone + "\"}";
    }

    private ResultActions update(String token, long id, String body) throws Exception {
        var request = put("/api/patients/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private static String newLastName() {
        return "Up" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void frontDeskDoctorsAndNursesCanUpdateAndTheVersionCountsUp() throws Exception {
        String last = newLastName();
        String created = registerPatient(last);
        long id = ((Number) JsonPath.read(created, "$.patientId")).longValue();
        long version = ((Number) JsonPath.read(created, "$.version")).longValue();

        for (Role role : new Role[] {Role.RECEPTIONIST, Role.DOCTOR, Role.NURSE}) {
            String phone = "9" + role.ordinal() + "999 88888";
            String updated = update(tokenFor(role), id, edit(last, version, phone))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.mrn").value((String) JsonPath.read(created, "$.mrn")))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn().getResponse().getContentAsString();
            version = ((Number) JsonPath.read(updated, "$.version")).longValue();
        }
    }

    @Test
    void theMrnInTheBodyIsNeverUsed() throws Exception {
        String last = newLastName();
        String created = registerPatient(last);
        long id = ((Number) JsonPath.read(created, "$.patientId")).longValue();
        String mrn = JsonPath.read(created, "$.mrn");
        String body = edit(last, 0, "99999 88888").replace("{", "{\"mrn\":\"HACK-1\",");

        var response = update(tokenFor(Role.RECEPTIONIST), id, body).andReturn().getResponse();

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isIn(200, 400);
        if (response.getStatus() == 200) {
            org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(response.getContentAsString(), "$.mrn")).isEqualTo(mrn);
        }
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        String last = newLastName();
        String created = registerPatient(last);
        long id = ((Number) JsonPath.read(created, "$.patientId")).longValue();
        String token = tokenFor(Role.RECEPTIONIST);
        update(token, id, edit(last, 0, "99999 88888")).andExpect(status().isOk());

        update(token, id, edit(last, 0, "11111 22222"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void theVersionIsRequiredAndInvalidInputIsRefused() throws Exception {
        String last = newLastName();
        long id = ((Number) JsonPath.read(registerPatient(last), "$.patientId")).longValue();
        String token = tokenFor(Role.RECEPTIONIST);

        update(token, id, edit(last, 0, "99999 88888").replace("\"version\":0,", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("version")));
        update(token, id, edit(last, 0, "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")));
    }

    @Test
    void anUnknownPatientIsNotFound() throws Exception {
        update(tokenFor(Role.RECEPTIONIST), 999_999_999L, edit(newLastName(), 0, "99999 88888"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void otherRolesAndAnonymousCallersAreRefused() throws Exception {
        String last = newLastName();
        long id = ((Number) JsonPath.read(registerPatient(last), "$.patientId")).longValue();

        for (Role role : new Role[] {Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            update(tokenFor(role), id, edit(last, 0, "99999 88888")).andExpect(status().isForbidden());
        }
        update(null, id, edit(last, 0, "99999 88888")).andExpect(status().isUnauthorized());
    }
}
