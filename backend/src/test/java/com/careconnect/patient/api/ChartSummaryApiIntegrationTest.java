package com.careconnect.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "careconnect.login.ip-max-failures=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChartSummaryApiIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";

    private record Login(String token, long userId) { }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private Login login(Role role, Long linkedPatientId) throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser user = users.save(AppUser.create(name, encoder.encode(PASSWORD), "User " + name, name + "@example.org",
            role, linkedPatientId, false));
        String json = "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}";
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Login("Bearer " + JsonPath.read(body, "$.accessToken"), user.getId());
    }

    private Login login(Role role) throws Exception {
        return login(role, null);
    }

    private long newPatient() throws Exception {
        String body = "{\"firstName\":\"Asha\",\"lastName\":\"Sm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
            + "\",\"dateOfBirth\":\"1990-05-17\",\"sex\":\"FEMALE\"}";
        String created = mvc.perform(post("/api/patients").header("Authorization", login(Role.RECEPTIONIST).token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.patientId")).longValue();
    }

    private void create(String token, String url, String body) throws Exception {
        mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private ResultActions summary(String token, long patientId) throws Exception {
        var request = get("/api/patients/" + patientId + "/summary");
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private long chartViewsBy(long patientId, long userId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = 'CHART_VIEWED' "
            + "and patient_id = ? and user_id = ?", Long.class, patientId, userId);
    }

    @Test
    void doctorsAndNursesSeeDemographicsAllergiesAndProblemsAndEachViewIsAudited() throws Exception {
        long patient = newPatient();
        String writer = login(Role.DOCTOR).token();
        create(writer, "/api/patients/" + patient + "/allergies", "{\"substance\":\"Dust\",\"severity\":\"MILD\"}");
        create(writer, "/api/patients/" + patient + "/allergies", "{\"substance\":\"Penicillin\",\"reaction\":\"Rash\",\"severity\":\"SEVERE\"}");
        create(writer, "/api/patients/" + patient + "/problems", "{\"icd10Code\":\"E11.9\",\"onsetDate\":\"2020-03-01\"}");

        for (Role role : new Role[] {Role.DOCTOR, Role.NURSE}) {
            Login who = login(role);
            summary(who.token(), patient)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.patient.patientId").value(patient))
                .andExpect(jsonPath("$.patient.firstName").value("Asha"))
                .andExpect(jsonPath("$.allergies", hasSize(2)))
                .andExpect(jsonPath("$.allergies[0].substance").value("Penicillin"))
                .andExpect(jsonPath("$.allergies[0].severity").value("SEVERE"))
                .andExpect(jsonPath("$.allergies[1].substance").value("Dust"))
                .andExpect(jsonPath("$.problems", hasSize(1)))
                .andExpect(jsonPath("$.problems[0].icd10Code").value("E11.9"))
                .andExpect(jsonPath("$.problems[0].description").value("Type 2 diabetes mellitus without complications"));
            assertThat(chartViewsBy(patient, who.userId())).as(role.name()).isEqualTo(1L);
        }
    }

    @Test
    void aPatientWithoutEntriesGetsEmptyLists() throws Exception {
        summary(login(Role.NURSE).token(), newPatient())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allergies", hasSize(0)))
            .andExpect(jsonPath("$.problems", hasSize(0)));
    }

    @Test
    void theFrontDeskOtherRolesPatientAccountsAndAnonymousCallersAreRefusedAndLeaveNoEntry() throws Exception {
        long patient = newPatient();

        for (Role role : new Role[] {Role.RECEPTIONIST, Role.PHARMACIST, Role.LAB_TECHNICIAN, Role.ADMIN}) {
            Login who = login(role);
            summary(who.token(), patient).andExpect(status().isForbidden());
            assertThat(chartViewsBy(patient, who.userId())).as(role.name()).isZero();
        }
        Login patientAccount = login(Role.PATIENT, patient);
        summary(patientAccount.token(), patient).andExpect(status().isForbidden());
        assertThat(chartViewsBy(patient, patientAccount.userId())).isZero();
        summary(null, patient).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownPatientIsNotFound() throws Exception {
        summary(login(Role.DOCTOR).token(), 999_999_999L)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
