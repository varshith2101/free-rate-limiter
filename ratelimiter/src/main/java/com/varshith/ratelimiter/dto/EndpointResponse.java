package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for endpoint with its rate limit configurations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointResponse {

    private UUID id;
    private String basePath;
    private String path;
    private String httpMethod;
    private String description;
    private UUID backendLinkId;
    private String backendLinkName;
    private String backendLinkColor;
    private String fullUrl;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Associated rate limit configurations
    private List<RateLimitConfigResponse> rateLimitConfigs;

    // Analytics summary (optional)
    private EndpointStats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointStats {
        private Long totalRequests;
        private Long blockedRequests;
        private Double blockRate; // percentage
        private LocalDateTime lastRequestAt;
    }
}
