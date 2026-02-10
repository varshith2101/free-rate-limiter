package com.varshith.ratelimiter.dto;

import com.varshith.ratelimiter.model.RateLimitConfig.Algorithm;
import com.varshith.ratelimiter.model.RateLimitConfig.LimitBy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a rate limit configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {

    /**
     * Tenant ID this config belongs to.
     */
    private UUID tenantId;

    /**
     * Optional: endpoint ID this config belongs to.
     */
    private UUID endpointId;

    /**
     * Endpoint pattern (supports wildcards: /api/users/*).
     */
    private String endpointPattern;

    /**
     * HTTP method (GET, POST, etc., or * for all).
     */
    @Builder.Default
    private String httpMethod = "*";

    /**
     * Maximum requests allowed in window.
     */
    @NotNull(message = "Max requests is required")
    @Min(value = 1, message = "Max requests must be at least 1")
    private Integer maxRequests;

    /**
     * Time window in seconds.
     */
    @NotNull(message = "Window seconds is required")
    @Min(value = 1, message = "Window must be at least 1 second")
    private Integer windowSeconds;

    /**
     * Rate limiting algorithm to use.
     */
    @NotNull(message = "Algorithm is required")
    private Algorithm algorithm;

    /**
     * What identifier to use for rate limiting.
     */
    @Builder.Default
    private LimitBy limitBy = LimitBy.IP;

    /**
     * Custom header name when limitBy = CUSTOM_HEADER.
     */
    private String customHeader;

    /**
     * Optional: Custom refill rate for TOKEN_BUCKET algorithm.
     * If not specified, will be calculated as maxRequests / windowSeconds.
     */
    private Double refillRate;
}
