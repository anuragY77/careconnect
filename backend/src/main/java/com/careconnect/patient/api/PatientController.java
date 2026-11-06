package com.careconnect.patient.api;

import java.net.URI;

import com.careconnect.patient.service.PatientService;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient registry. Reading a chart is added together with its audit entry (BR-12), so there is no
 * GET here yet: an endpoint that returns patient data without recording the access would break the rule.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @PostMapping
    @RegistrationStaff
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        PatientResponse created = PatientResponse.from(service.register(request.toCommand(), request.confirmNotDuplicate()));
        return ResponseEntity.created(URI.create("/api/patients/" + created.patientId()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(created);
    }
}
