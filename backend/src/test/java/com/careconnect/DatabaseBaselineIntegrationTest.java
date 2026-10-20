package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the whole application against a real, throw-away PostgreSQL 16
 * container. Needs Docker running. Proves that:
 * - the application context starts with Hibernate schema validation on,
 * - Flyway applied the baseline migration,
 * - the health probe is public and everything else is denied by default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DatabaseBaselineIntegrationTest {

    @Test
    void flywayAppliedTheBaselineMigration(@Autowired DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select success from flyway_schema_history where version = '1'")) {
            assertThat(rs.next()).as("migration V1 is recorded").isTrue();
            assertThat(rs.getBoolean(1)).as("migration V1 succeeded").isTrue();
        }
    }

    @Test
    void healthEndpointIsPublicAndUp(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void everythingElseIsDeniedByDefault(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().is4xxClientError());
    }
}
