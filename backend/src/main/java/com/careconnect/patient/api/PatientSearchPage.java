package com.careconnect.patient.api;

import java.util.List;

/** One page of search results. An empty {@code content} with {@code totalElements} 0 means nothing matched. */
public record PatientSearchPage(List<PatientSummaryResponse> content, int page, int size, long totalElements,
                                int totalPages) {
}
