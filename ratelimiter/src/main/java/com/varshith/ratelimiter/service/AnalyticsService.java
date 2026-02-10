package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.AnalyticsResponse;
import com.varshith.ratelimiter.repository.EndpointRepository;
import com.varshith.ratelimiter.repository.RateLimitLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service for analytics and monitoring data.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final RateLimitLogRepository logRepository;
    private final EndpointRepository endpointRepository;

    // Country code to name mapping (can be extended)
    private static final HashMap<String, String> COUNTRY_NAMES = new HashMap<>();

    static {
        COUNTRY_NAMES.put("US", "United States");
        COUNTRY_NAMES.put("IN", "India");
        COUNTRY_NAMES.put("GB", "United Kingdom");
        COUNTRY_NAMES.put("CA", "Canada");
        COUNTRY_NAMES.put("AU", "Australia");
        COUNTRY_NAMES.put("DE", "Germany");
        COUNTRY_NAMES.put("FR", "France");
        COUNTRY_NAMES.put("JP", "Japan");
        COUNTRY_NAMES.put("CN", "China");
        COUNTRY_NAMES.put("BR", "Brazil");
        // Add more as needed
    }

    public AnalyticsService(RateLimitLogRepository logRepository, EndpointRepository endpointRepository) {
        this.logRepository = logRepository;
        this.endpointRepository = endpointRepository;
    }

    /**
     * Get analytics for a tenant within a time range.
     */
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(UUID tenantId, LocalDateTime start, LocalDateTime end) {
        log.debug("Fetching analytics for tenant: {} from {} to {}", tenantId, start, end);

        AnalyticsResponse response = new AnalyticsResponse();

        // Summary stats
        response.setSummary(getSummary(tenantId, start, end));

        // Time series data (hourly buckets)
        response.setTimeSeries(getTimeSeries(tenantId, start, end));

        // Top endpoints
        response.setTopEndpoints(getTopEndpoints(tenantId, start, end));

        // Geographic distribution (disabled for now)
        response.setGeographicDistribution(List.of());

        return response;
    }

    /**
     * Get summary statistics.
     */
    private AnalyticsResponse.Summary getSummary(UUID tenantId, LocalDateTime start, LocalDateTime end) {
        long totalRequests = logRepository.countByTenantIdAndTimestampBetween(tenantId, start, end);
        long blockedRequests = logRepository.countBlockedByTenantIdAndTimestampBetween(tenantId, start, end);
        long allowedRequests = totalRequests - blockedRequests;

        double blockRate = totalRequests > 0
                ? (double) blockedRequests / totalRequests * 100
                : 0.0;

        return AnalyticsResponse.Summary.builder()
                .totalRequests(totalRequests)
                .allowedRequests(allowedRequests)
                .blockedRequests(blockedRequests)
                .blockRate(Math.round(blockRate * 100.0) / 100.0)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }

    /**
     * Get time series data (hourly buckets).
     */
    private List<AnalyticsResponse.TimeSeriesPoint> getTimeSeries(
            UUID tenantId,
            LocalDateTime start,
            LocalDateTime end) {

        List<Object[]> hourlyStats = logRepository.getHourlyStats(tenantId, start, end);
        List<AnalyticsResponse.TimeSeriesPoint> points = new ArrayList<>();

        for (Object[] stat : hourlyStats) {
            LocalDateTime timestamp;
            Object rawTimestamp = stat[0];

            if (rawTimestamp instanceof Timestamp) {
                timestamp = ((Timestamp) rawTimestamp).toLocalDateTime();
            } else if (rawTimestamp instanceof LocalDateTime) {
                timestamp = (LocalDateTime) rawTimestamp;
            } else {
                continue;
            }

            Long totalCount = ((Number) stat[1]).longValue();
            Long blockedCount = ((Number) stat[2]).longValue();

            points.add(AnalyticsResponse.TimeSeriesPoint.builder()
                    .timestamp(timestamp)
                    .totalRequests(totalCount)
                    .blockedRequests(blockedCount)
                    .build());
        }

        return points;
    }

    /**
     * Get top endpoints by request count.
     */
    private List<AnalyticsResponse.EndpointStat> getTopEndpoints(
            UUID tenantId,
            LocalDateTime start,
            LocalDateTime end) {

        List<Object[]> endpointStats = logRepository.countByEndpointGrouped(tenantId, start, end);
        List<AnalyticsResponse.EndpointStat> stats = new ArrayList<>();

        for (Object[] stat : endpointStats) {
            UUID endpointId = (UUID) stat[0];
            Long count = ((Number) stat[1]).longValue();

            var endpoint = endpointRepository.findById(endpointId).orElse(null);
            String endpointLabel = endpoint != null ? endpoint.getPath() : endpointId.toString();
            String methodLabel = endpoint != null ? endpoint.getHttpMethod() : "*";

            stats.add(AnalyticsResponse.EndpointStat.builder()
                .endpoint(endpointLabel)
                .method(methodLabel)
                    .requestCount(count)
                    .blockedCount(0L) // Can be calculated if needed
                    .blockRate(0.0)
                    .build());
        }

        // Sort by request count descending and take top 10
        stats.sort((a, b) -> Long.compare(b.getRequestCount(), a.getRequestCount()));
        return stats.size() > 10 ? stats.subList(0, 10) : stats;
    }

    /**
     * Get geographic distribution of requests.
     */
    private List<AnalyticsResponse.CountryStat> getGeographicDistribution(
            UUID tenantId,
            LocalDateTime start,
            LocalDateTime end) {

        List<Object[]> countryStats = logRepository.countByCountryGrouped(tenantId, start, end);
        List<AnalyticsResponse.CountryStat> stats = new ArrayList<>();

        for (Object[] stat : countryStats) {
            String countryCode = (String) stat[0];
            Long count = ((Number) stat[1]).longValue();

            String countryName = COUNTRY_NAMES.getOrDefault(countryCode, countryCode);

            stats.add(AnalyticsResponse.CountryStat.builder()
                    .countryCode(countryCode)
                    .countryName(countryName)
                    .requestCount(count)
                    .build());
        }

        // Sort by request count descending
        stats.sort((a, b) -> Long.compare(b.getRequestCount(), a.getRequestCount()));
        return stats;
    }
}
