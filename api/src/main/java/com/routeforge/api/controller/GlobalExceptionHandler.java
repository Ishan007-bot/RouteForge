package com.routeforge.api.controller;

import com.routeforge.api.dto.ApiErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Turns exceptions into clean JSON error responses.
 * <p>
 * Without this, Spring returns a default whitelabel error page and 500s for
 * anything not caught — terrible UX for an API client. We catch the common
 * cases and map them to the right HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failures on @RequestBody DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDto.of("validation_failed", "Request body is invalid", details));
    }

    /** Bad argument values raised from the engine (unknown profile, etc.). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDto> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDto.of("bad_request", e.getMessage()));
    }

    /** Engine state issues (e.g. empty graph). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDto> serviceUnavailable(IllegalStateException e) {
        log.warn("service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorDto.of("service_unavailable", e.getMessage()));
    }

    /** Anything else. We log the stack trace so we can debug post-mortem. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> unhandled(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorDto.of("internal_error", "Something went wrong"));
    }
}
