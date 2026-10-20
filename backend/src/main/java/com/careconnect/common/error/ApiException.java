package com.careconnect.common.error;

/**
 * Exception that carries a stable {@link ErrorCode}. The message is sent to
 * the client as the problem detail, so it must be written for users and must
 * never contain patient data or internal details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

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
}
