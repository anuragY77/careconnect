package com.careconnect.auth.api;

import java.util.List;

/** One page of staff accounts. */
public record StaffAccountPage(List<StaffAccountResponse> items, int page, int size, long totalElements,
                               int totalPages) {
}
