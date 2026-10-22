package com.careconnect.common.error;

/** Thrown when a client exceeded a rate limit. The response carries a Retry-After header. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String detail, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, detail);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /** Seconds the client should wait; never less than 1. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
