package com.varshith.ratelimiter.dto;

import com.varshith.ratelimiter.model.RateLimitConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for rate limit configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfigResponse {

    private UUID id;
    private UUID endpointId;
    private String endpointPattern;
    private String httpMethod;
    private Integer maxRequests;
    private Integer windowSeconds;
    private RateLimitConfig.Algorithm algorithm;
    private RateLimitConfig.LimitBy limitBy;
    private String customHeader;
    private Double refillRate;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
