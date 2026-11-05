package com.careconnect.auth.api;

import com.careconnect.auth.service.StaffAccountService;
import com.careconnect.auth.service.StaffAccountView;
import com.careconnect.common.security.AdminOnly;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff account management for administrators (FR-AUTH-02). */
@RestController
@RequestMapping("/api/admin/staff-accounts")
@AdminOnly
public class StaffAccountController {

    static final int MAX_PAGE_SIZE = 100;

    private final StaffAccountService service;

    public StaffAccountController(StaffAccountService service) {
        this.service = service;
    }

    @GetMapping
    public StaffAccountPage list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<StaffAccountView> result = service.list(PageRequest.of(safePage, safeSize, Sort.by("id")));
        return new StaffAccountPage(result.map(StaffAccountResponse::from).getContent(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public StaffAccountResponse get(@PathVariable long id) {
        return StaffAccountResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffCredentialResponse create(@Valid @RequestBody CreateStaffRequest request,
                                          HttpServletResponse response) {
        noStore(response);
        return StaffCredentialResponse.from(
            service.create(request.username(), request.fullName(), request.email(), request.role()));
    }

    @PutMapping("/{id}")
    public StaffAccountResponse update(@PathVariable long id, @Valid @RequestBody UpdateStaffRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return StaffAccountResponse.from(
            service.update(actor(jwt), id, request.fullName(), request.email(), request.role()));
    }

    @PostMapping("/{id}/deactivate")
    public StaffAccountResponse deactivate(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return StaffAccountResponse.from(service.deactivate(actor(jwt), id));
    }

    @PostMapping("/{id}/activate")
    public StaffAccountResponse activate(@PathVariable long id) {
        return StaffAccountResponse.from(service.activate(id));
    }

    @PostMapping("/{id}/unlock")
    public StaffAccountResponse unlock(@PathVariable long id) {
        return StaffAccountResponse.from(service.unlock(id));
    }

    @PostMapping("/{id}/reset-password")
    public StaffCredentialResponse resetPassword(@PathVariable long id, HttpServletResponse response) {
        noStore(response);
        return StaffCredentialResponse.from(service.resetPassword(id));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
