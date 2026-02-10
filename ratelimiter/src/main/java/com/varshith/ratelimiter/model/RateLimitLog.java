package com.varshith.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rate limit log entry for analytics and monitoring.
 *
 * Tracks all rate limit checks for analysis, debugging, and visualization.
 */
@Entity
@Table(name = "rate_limit_logs", indexes = {
    @Index(name = "idx_tenant_timestamp", columnList = "tenant_id, timestamp"),
    @Index(name = "idx_endpoint_timestamp", columnList = "endpoint_id, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private RateLimitConfig config;

    /**
     * When the request was made.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Client identifier (IP, user ID, etc.)
     */
    @Column(name = "client_identifier", nullable = false)
    private String clientIdentifier;

    /**
     * The type of identifier used.
     */
    @Column(name = "identifier_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RateLimitConfig.LimitBy identifierType;

    /**
     * Whether the request was allowed.
     */
    @Column(nullable = false)
    private Boolean allowed;

    /**
     * Current request count at time of check.
     */
    @Column(name = "current_count")
    private Integer currentCount;

    /**
     * Maximum allowed requests.
     */
    @Column(name = "max_requests")
    private Integer maxRequests;

    /**
     * Algorithm used for this check.
     */
    @Column(name = "algorithm")
    @Enumerated(EnumType.STRING)
    private RateLimitConfig.Algorithm algorithm;

    /**
     * HTTP method of the request.
     */
    @Column(name = "http_method")
    private String httpMethod;

    /**
     * Request path.
     */
    @Column(name = "request_path")
    private String requestPath;

    /**
     * Geographic location (if available).
     * Example: "US", "IN", "GB"
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /**
     * Response time in milliseconds.
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
}
