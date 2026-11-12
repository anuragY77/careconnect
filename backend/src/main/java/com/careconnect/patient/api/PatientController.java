package com.careconnect.patient.api;

import java.net.URI;

import com.careconnect.patient.service.PatientSearchService;
import com.careconnect.patient.service.PatientService;
import com.careconnect.patient.service.PatientSummary;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient registry. Opening a chart is added together with its audit entry (BR-12), so there is no
 * GET by id here yet: an endpoint that returns a whole patient without recording the access would break
 * the rule. The search returns only a short summary line per patient.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;
    private final PatientSearchService search;

    public PatientController(PatientService service, PatientSearchService search) {
        this.service = service;
        this.search = search;
    }

    /** Search by name, MRN, phone number or date of birth. {@code q} is required; the page size is at most 50. */
    @GetMapping
    @PatientSearchStaff
    public PatientSearchPage search(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Page<PatientSummary> result = search.search(q, page, size);
        return new PatientSearchPage(result.map(PatientSummaryResponse::from).getContent(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @RegistrationStaff
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        PatientResponse created = PatientResponse.from(service.register(request.toCommand(),
            Boolean.TRUE.equals(request.confirmNotDuplicate())));
        return ResponseEntity.created(URI.create("/api/patients/" + created.patientId()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(created);
    }

    /** Replaces the demographics. The body carries the version the screen was showing (see {@link UpdatePatientRequest}). */
    @PutMapping("/{id}")
    @RegistrationStaff
    public PatientResponse update(@PathVariable long id, @Valid @RequestBody UpdatePatientRequest request,
                                  HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return PatientResponse.from(service.update(id, request.version(), request.toCommand()));
    }
}
