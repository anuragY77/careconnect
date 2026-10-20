package com.careconnect.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks the real error responses of the running application: media type,
 * stable code, status, field errors, and that internal details never leak.
 * A throw-away probe controller under /test-errors provides the failures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ErrorHandlingIntegrationTest {

    public record SampleRequest(@NotBlank String name, @Min(1) int age) {
    }

    @RestController
    @RequestMapping("/test-errors")
    public static class ErrorProbeController {

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }

        @GetMapping("/not-found")
        public String notFound() {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Patient 42 was not found.");
        }

        @GetMapping("/rule")
        public String rule() {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "A contraindicated order cannot be overridden.");
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        ErrorProbeController errorProbeController() {
            return new ErrorProbeController();
        }

        /** Opens only the probe paths so the tests reach the controller; production rules stay untouched. */
        @Bean
        @Order(0)
        SecurityFilterChain probeSecurityChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/test-errors/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    void validationFailureListsInvalidFields(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(post("/test-errors/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"age\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void malformedJsonIsReportedAsMalformedRequest(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(post("/test-errors/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void apiExceptionKeepsItsCodeStatusAndMessage(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/test-errors/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("Patient 42 was not found."))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void businessRuleViolationIsUnprocessable(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/test-errors/rule"))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void unknownPathIsNotFound(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/test-errors/nope"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongMethodIsMethodNotAllowed(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(delete("/test-errors/validate"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedFailureNeverLeaksInternalDetail(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/test-errors/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.errorId").isNotEmpty())
            .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
