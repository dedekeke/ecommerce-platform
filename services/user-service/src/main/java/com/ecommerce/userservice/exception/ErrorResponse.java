package com.ecommerce.userservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Error Response DTO
 *
 * Standardized error response structure for API errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Error type/code (e.g., "NOT_FOUND", "VALIDATION_ERROR")
     */
    private String error;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * API path where the error occurred
     */
    private String path;

    /**
     * Timestamp when the error occurred
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Validation errors (field-specific errors)
     */
    private Map<String, String> validationErrors;

    /**
     * Additional error details
     */
    private List<String> details;

    /**
     * Trace ID for debugging (from distributed tracing)
     */
    private String traceId;

}
