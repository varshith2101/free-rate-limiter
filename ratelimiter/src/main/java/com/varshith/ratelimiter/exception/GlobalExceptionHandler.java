package com.varshith.ratelimiter.exception;

import com.varshith.ratelimiter.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers.
 *
 * Converts exceptions to consistent ErrorResponse format with proper HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle API key not found exceptions.
     * Returns 401 Unauthorized.
     */
    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleApiKeyNotFound(
            ApiKeyNotFoundException ex,
            HttpServletRequest request) {

        log.warn("API key not found: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handle rate limit exceeded exceptions.
     * Returns 429 Too Many Requests with X-RateLimit headers.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.info("Rate limit exceeded: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        // Include rate limit metadata in response
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("limit", ex.getLimit());
        metadata.put("resetAt", ex.getResetAt());
        metadata.put("retryAfter", ex.getResetAt() - System.currentTimeMillis() / 1000);

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                ex.getMessage(),
                request.getRequestURI(),
                metadata
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(ex.getResetAt()))
                .header("Retry-After", String.valueOf(metadata.get("retryAfter")))
                .body(error);
    }

    /**
     * Handle invalid configuration exceptions.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(InvalidConfigException.class)
    public ResponseEntity<ErrorResponse> handleInvalidConfig(
            InvalidConfigException ex,
            HttpServletRequest request) {

        log.warn("Invalid configuration: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle tenant not found exceptions.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(
            TenantNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Tenant not found: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle validation errors (from @Valid annotations).
     * Returns 400 Bad Request with field-specific errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation error - Path: {}", request.getRequestURI());

        // Collect field errors
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid request parameters",
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle all other exceptions.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error - Path: {}", request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
