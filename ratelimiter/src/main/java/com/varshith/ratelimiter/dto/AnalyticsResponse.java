package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for analytics dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private Summary summary;
    private List<TimeSeriesPoint> timeSeries;
    private List<EndpointStat> topEndpoints;
    private List<CountryStat> geographicDistribution;
    private List<RecentLog> recentLogs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long totalRequests;
        private Long allowedRequests;
        private Long blockedRequests;
        private Double blockRate;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private LocalDateTime timestamp;
        private Long totalRequests;
        private Long blockedRequests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointStat {
        private String endpoint;
        private String method;
        private Long requestCount;
        private Long blockedCount;
        private Double blockRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryStat {
        private String countryCode;
        private String countryName;
        private Long requestCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentLog {
        private LocalDateTime timestamp;
        private String endpoint;
        private String method;
        private String clientIdentifier;
        private Boolean allowed;
        private String algorithm;
    }
}
