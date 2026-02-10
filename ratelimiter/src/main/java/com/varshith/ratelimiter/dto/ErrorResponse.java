package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard error response DTO for all API errors.
 *
 * Follows RFC 7807 (Problem Details for HTTP APIs) style.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * ISO 8601 timestamp when error occurred.
     */
    private String timestamp;

    /**
     * HTTP status code (400, 401, 404, 429, 500, etc.).
     */
    private int status;

    /**
     * HTTP status reason phrase (Bad Request, Unauthorized, etc.).
     */
    private String error;

    /**
     * Human-readable error message.
     */
    private String message;

    /**
     * Request path that caused the error.
     */
    private String path;

    /**
     * Optional: Additional metadata (e.g., resetAt for 429 errors).
     */
    private Object metadata;

    /**
     * Create error response with current timestamp.
     */
    public static ErrorResponse create(int status, String error, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();
    }

    /**
     * Create error response with metadata.
     */
    public static ErrorResponse create(int status, String error, String message, String path, Object metadata) {
        return ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .metadata(metadata)
                .build();
    }
}
