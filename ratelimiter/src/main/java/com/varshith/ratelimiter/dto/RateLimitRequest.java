package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for rate limit checking.
 *
 * Used by clients to check if a request should be allowed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRequest {

    /**
     * Endpoint path to check rate limit for.
     * Example: "/api/users", "/api/posts/123"
     */
    @NotBlank(message = "Endpoint is required")
    private String endpoint;

    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     * Defaults to GET if not specified.
     */
    private String method = "GET";

    /**
     * Optional: Client IP address for additional tracking.
     * Can be used for IP-based rate limiting in addition to API key.
     */
    private String clientIp;

    /**
     * Optional: User identifier for per-user rate limiting.
     * Useful when API key is shared but user-level limits are needed.
     */
    private String userId;
}
