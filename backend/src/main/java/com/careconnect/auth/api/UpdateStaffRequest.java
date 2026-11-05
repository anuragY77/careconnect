package com.careconnect.auth.api;

import com.careconnect.auth.domain.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/admin/staff-accounts/{id}. The username never changes. */
public record UpdateStaffRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull Role role) {
}
