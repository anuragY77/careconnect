package com.careconnect.patient.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Patient search through the full security chain (TC-12, TC-13). */
// Same settings as the other API tests, so all share one application context.
@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PatientSearchApiIntegrationTest {

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

    private String registerPatient(String token, String lastName) throws Exception {
        String body = "{\"firstName\":\"Asha\",\"lastName\":\"" + lastName + "\",\"dateOfBirth\":\"1990-05-17\","
            + "\"sex\":\"FEMALE\",\"phone\":\"98765 43210\",\"address\":\"12 Park Road\",\"email\":\"asha@example.org\","
            + "\"insuranceProvider\":\"Star Health\",\"confirmNotDuplicate\":true}";
        String created = mvc.perform(post("/api/patients").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.mrn");
    }

    private ResultActions search(String token, String query) throws Exception {
        var request = get("/api/patients").param("q", query);
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    @Test
    void frontDeskDoctorsAndNursesFindAPatientAndSeeOnlyASummary() throws Exception {
        String desk = tokenFor(Role.RECEPTIONIST);
        String last = "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String mrn = registerPatient(desk, last);

        for (Role role : new Role[] {Role.RECEPTIONIST, Role.DOCTOR, Role.NURSE}) {
            search(tokenFor(role), last)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mrn").value(mrn))
                .andExpect(jsonPath("$.content[0].lastName").value(last))
                .andExpect(jsonPath("$.content[0].dateOfBirth").value("1990-05-17"))
                .andExpect(jsonPath("$.content[0].address").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].insuranceProvider").doesNotExist());
        }
        search(desk, mrn).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void nothingFoundIsAnEmptyListWithACount() throws Exception {
        search(tokenFor(Role.RECEPTIONIST), "Zq" + UUID.randomUUID().toString().replace("-", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anEmptySearchIsRefused() throws Exception {
        String token = tokenFor(Role.RECEPTIONIST);

        search(token, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/patients").header("Authorization", token))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void thePageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/patients").param("q", "Zq").param("size", "1000")
                .header("Authorization", tokenFor(Role.RECEPTIONIST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void otherRolesAndAnonymousCallersAreRefused() throws Exception {
        for (Role role : new Role[] {Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            search(tokenFor(role), "Asha").andExpect(status().isForbidden());
        }
        search(null, "Asha").andExpect(status().isUnauthorized());
    }
}
