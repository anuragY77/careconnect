package com.careconnect.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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

/** Patient registration through the full security chain (TC-10). */
// Same settings as the other API tests, so all share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PatientApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";
    private static final String VALID = "{\"firstName\":\"Asha\",\"lastName\":\"Rao\",\"dateOfBirth\":\"1990-05-17\","
        + "\"sex\":\"FEMALE\",\"phone\":\"98765 43210\",\"email\":\"asha@example.org\"}";

    private static String confirmed(String body) {
        return body.replace("}", ",\"confirmNotDuplicate\":true}");
    }

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

    private org.springframework.test.web.servlet.ResultActions register(String token, String body) throws Exception {
        var request = post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    @Test
    void frontDeskDoctorsAndNursesCanRegisterAPatient() throws Exception {
        for (Role role : new Role[] {Role.RECEPTIONIST, Role.DOCTOR, Role.NURSE}) {
            register(tokenFor(role), confirmed(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/patients/\\d+")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.mrn").value(matchesPattern("CC-\\d{6,}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.firstName").value("Asha"));
        }
    }

    @Test
    void theMrnCanNotBeChosenByTheCaller() throws Exception {
        String body = confirmed(VALID.replace("{", "{\"mrn\":\"HACK-1\",\"patientId\":1,\"status\":\"INACTIVE\","));

        var result = register(tokenFor(Role.RECEPTIONIST), body).andReturn().getResponse();

        // Either the unknown fields are ignored or the request is refused; they are never used.
        assertThat(result.getStatus()).isIn(201, 400);
        if (result.getStatus() == 201) {
            assertThat((String) JsonPath.read(result.getContentAsString(), "$.mrn")).matches("CC-\\d{6,}");
            assertThat((String) JsonPath.read(result.getContentAsString(), "$.status")).isEqualTo("ACTIVE");
        }
    }

    @Test
    void otherRolesAndAnonymousCallersAreRefused() throws Exception {
        for (Role role : new Role[] {Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            register(tokenFor(role), VALID).andExpect(status().isForbidden());
        }
        register(null, VALID).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputIsRefusedWithTheFieldsNamed() throws Exception {
        String bad = "{\"firstName\":\" \",\"lastName\":\"Rao\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\","
            + "\"phone\":\"abc\",\"email\":\"not-an-email\"}";

        register(tokenFor(Role.RECEPTIONIST), bad)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("firstName")))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")))
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
    }

    @Test
    void possibleDuplicatesRequireExplicitConfirmation() throws Exception {
        String token = tokenFor(Role.RECEPTIONIST);
        String name = "Duplicate-" + UUID.randomUUID().toString().substring(0, 8);
        String phone = "98765" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 7);
        String body = VALID.replace("Asha", name).replace("Rao", "Example").replace("98765 43210", phone);

        register(token, body).andExpect(status().isCreated());

        register(token, body)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE"))
            .andExpect(jsonPath("$.possibleDuplicates[0].firstName").value(name))
            .andExpect(jsonPath("$.possibleDuplicates[0].mrn").value(matchesPattern("CC-\\d{6,}")));

        register(token, confirmed(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value(name));
    }

    @Test
    void aFutureDateOfBirthAndAMissingSexAreRefused() throws Exception {
        String token = tokenFor(Role.RECEPTIONIST);

        register(token, VALID.replace("1990-05-17", "2999-01-01"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register(token, VALID.replace("\"sex\":\"FEMALE\",", ""))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register(token, VALID.replace("FEMALE", "ROBOT"))
            .andExpect(status().isBadRequest());
    }
}
