package com.careconnect.patient.api;

import java.net.URI;

import com.careconnect.patient.service.ProblemService;

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

@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService service;

    public ProblemController(ProblemService service) {
        this.service = service;
    }

    @PostMapping("/patients/{patientId}/problems")
    @ChartWriteStaff
    public ResponseEntity<ProblemResponse> add(@PathVariable long patientId,
                                               @Valid @RequestBody AddProblemRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        ProblemResponse created = ProblemResponse.from(service.add(Long.parseLong(jwt.getSubject()), patientId,
            request.icd10Code(), request.onsetDate()));
        return ResponseEntity.created(URI.create("/api/problems/" + created.problemId()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(created);
    }

    @PatchMapping("/problems/{problemId}/status")
    @ChartWriteStaff
    public ProblemResponse changeStatus(@PathVariable long problemId,
                                        @Valid @RequestBody ChangeProblemStatusRequest request,
                                        HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ProblemResponse.from(service.changeStatus(problemId, request.version(), request.status(),
            request.reason()));
    }
}
