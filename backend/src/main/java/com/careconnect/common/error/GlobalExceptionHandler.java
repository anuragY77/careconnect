package com.careconnect.common.error;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 7807 problem response with a stable
 * {@code code}. Framework exceptions (bad JSON, wrong method, unknown path,
 * validation) are handled by the parent class and decorated here; our own
 * exceptions and common persistence and security exceptions are mapped
 * explicitly. Anything unexpected becomes INTERNAL_ERROR with a reference id
 * and no internal detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatusCode.valueOf(ErrorCode.TOO_MANY_REQUESTS.status()))
            .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
            .body(problem(ErrorCode.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
            .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
            "The record was changed by another user. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.CONFLICT, "The request conflicts with existing data.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception, errorId={}", errorId, ex);
        ProblemDetail problem = problem(ErrorCode.INTERNAL_ERROR,
            "An unexpected error occurred. Quote reference " + errorId + " when reporting it.");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    /** Decorates the problem bodies that the parent class builds for framework exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ErrorCode code = codeFor(ex, problem.getStatus());
            decorate(problem, code);
            if (ex instanceof MethodArgumentNotValidException invalid) {
                problem.setDetail("One or more fields are invalid.");
                List<FieldViolation> errors = invalid.getBindingResult().getFieldErrors().stream()
                    .map(e -> new FieldViolation(e.getField(), Objects.toString(e.getDefaultMessage(), "invalid")))
                    .toList();
                problem.setProperty("errors", errors);
            }
        }
        return response;
    }

    private static ErrorCode codeFor(Exception ex, int status) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.fromStatus(status);
    }

    /**
     * Builds the response for a coded error. Every 401 carries a {@code WWW-Authenticate} challenge,
     * as HTTP requires, so it looks the same whether the security filter or a controller refused it.
     * "Bearer" does not make browsers show a login dialog (only "Basic" does).
     */
    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatusCode.valueOf(code.status()));
        if (code == ErrorCode.UNAUTHENTICATED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(problem(code, detail));
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.status()), detail);
        decorate(problem, code);
        return problem;
    }

    private static void decorate(ProblemDetail problem, ErrorCode code) {
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
    }
}
