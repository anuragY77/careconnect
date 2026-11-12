package com.careconnect.patient.api;

import java.net.URI;

import com.careconnect.patient.service.AllergyService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allergy list. There is no endpoint to read or to delete an allergy: reading comes with the chart view and
 * its audit entry (BR-12), and clinical data is never deleted (BR-08).
 */
@RestController
@RequestMapping("/api")
public class AllergyController {

    private final AllergyService service;

    public AllergyController(AllergyService service) {
        this.service = service;
    }

    @PostMapping("/patients/{patientId}/allergies")
    @ChartWriteStaff
    public ResponseEntity<AllergyResponse> add(@PathVariable long patientId,
                                               @Valid @RequestBody AddAllergyRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        AllergyResponse created = AllergyResponse.from(service.add(actor(jwt), patientId, request.substance(),
            request.reaction(), request.severity()));
        return ResponseEntity.created(URI.create("/api/allergies/" + created.allergyId()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(created);
    }

    @PatchMapping("/allergies/{allergyId}/status")
    @ChartWriteStaff
    public AllergyResponse changeStatus(@PathVariable long allergyId,
                                        @Valid @RequestBody ChangeAllergyStatusRequest request,
                                        HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return AllergyResponse.from(service.changeStatus(allergyId, request.version(), request.status(),
            request.reason()));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
