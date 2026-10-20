package com.careconnect.common.error;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of
 * every error response. Clients and tests rely on these names, so never
 * rename or reuse one; add a new constant instead.
 */
public enum ErrorCode {

    MALFORMED_REQUEST(400, "Malformed request"),
    VALIDATION_FAILED(400, "Validation failed"),
    UNAUTHENTICATED(401, "Authentication required"),
    ACCESS_DENIED(403, "Access denied"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    CONFLICT(409, "Conflict with existing data"),
    OPTIMISTIC_LOCK_CONFLICT(409, "Record was changed by someone else"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    BUSINESS_RULE_VIOLATION(422, "Business rule violated"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal error");

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem type identifier, for example {@code urn:careconnect:error:validation-failed}. */
    public String typeUri() {
        return "urn:careconnect:error:" + name().toLowerCase().replace('_', '-');
    }

    /** Best matching code for an HTTP status produced by the framework. */
    public static ErrorCode fromStatus(int status) {
        for (ErrorCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
    }
}
