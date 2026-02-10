package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for checking rate limit (used by client applications).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRateLimitRequest {

    @NotBlank(message = "Endpoint is required")
    private String endpoint;

    @NotBlank(message = "HTTP method is required")
    private String method;

    /**
     * Client IP address (for IP-based rate limiting).
     * If not provided, will be extracted from request headers.
     */
    private String clientIp;

    /**
     * User ID (for user-based rate limiting).
     * Example: "user_12345"
     */
    private String userId;

    /**
     * Custom headers for rate limiting.
     * Example: {"X-Client-ID": "abc123"}
     */
    private Map<String, String> headers;
}
