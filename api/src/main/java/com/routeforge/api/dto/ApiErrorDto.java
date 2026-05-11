package com.routeforge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned for any 4xx/5xx response. */
@Schema(description = "A structured error response.")
public record ApiErrorDto(
        @Schema(description = "Short error code, e.g. 'validation_failed'.") String code,
        @Schema(description = "Human-readable message.") String message,
        @Schema(description = "Optional per-field details for validation errors.") List<String> details,
        @Schema(description = "Server time the error was generated.") Instant timestamp
) {

    public static ApiErrorDto of(String code, String message) {
        return new ApiErrorDto(code, message, List.of(), Instant.now());
    }

    public static ApiErrorDto of(String code, String message, List<String> details) {
        return new ApiErrorDto(code, message, details, Instant.now());
    }
}
