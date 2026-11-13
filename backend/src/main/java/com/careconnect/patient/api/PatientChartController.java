package com.careconnect.patient.api;

import com.careconnect.common.security.SecurityClaims;
import com.careconnect.patient.service.ChartActor;
import com.careconnect.patient.service.PatientChartService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientChartController {

    private final PatientChartService service;

    public PatientChartController(PatientChartService service) {
        this.service = service;
    }

    @GetMapping("/{patientId}")
    @PatientSearchStaff
    public PatientResponse open(@PathVariable long patientId, @AuthenticationPrincipal Jwt jwt,
                                HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return PatientResponse.from(service.open(actor(jwt), patientId));
    }

    private static ChartActor actor(Jwt jwt) {
        Object patientClaim = jwt.getClaim(SecurityClaims.PATIENT_ID);
        Long patientId = patientClaim instanceof Number number ? number.longValue() : null;
        return new ChartActor(jwt.getClaimAsString(SecurityClaims.ROLE), patientId);
    }
}
