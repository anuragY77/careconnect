package com.careconnect.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception that carries a stable {@link ErrorCode}. The message is sent to
 * the client as the problem detail, so it must be written for users and must
 * never contain patient data or internal details. Extra {@link #with fields} may carry data
 * the signed-in caller is allowed to see (the possible-duplicate list), never internals.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Adds a field to the problem body, for example the list of possible duplicates. */
    public ApiException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
