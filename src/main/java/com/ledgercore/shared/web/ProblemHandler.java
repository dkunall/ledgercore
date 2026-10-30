package com.ledgercore.shared.web;

import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.DomainException;
import com.ledgercore.shared.error.InvalidRequestException;
import com.ledgercore.shared.error.NotFoundException;
import com.ledgercore.shared.error.StateConflictException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves as application/problem+json (RFC 7807) with a stable {@code code} clients can switch on.
 */
@RestControllerAdvice
public class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        HttpStatus status = switch (e) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case StateConflictException ignored -> HttpStatus.CONFLICT;
            case BusinessRuleException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case InvalidRequestException ignored -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(problem(status, e.code(), e.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "validation-failed", e.getMessage()));
    }

    /**
     * lock_timeout fired: another transaction held a row we needed for too long. Safe to retry with the same key.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockTimeout(PessimisticLockingFailureException e) {
        log.warn("Lock wait timed out: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "1")
            .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Resource is busy, retry shortly"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.internalServerError()
            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error"));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "validation-failed", ex.getMessage()));
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:ledgercore:problem:" + code));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
