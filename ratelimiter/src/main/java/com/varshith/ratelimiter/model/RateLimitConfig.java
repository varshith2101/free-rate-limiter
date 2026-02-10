package com.varshith.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rate limit configuration entity.
 *
 * Defines rate limiting rules for specific endpoints and tenants.
 * Supports multiple algorithms and flexible endpoint pattern matching.
 */
@Entity
@Table(name = "rate_limit_configs", indexes = {
    @Index(name = "idx_tenant_endpoint", columnList = "tenant_id, endpoint_pattern")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Optional reference to specific endpoint.
     * If set, this config applies only to that endpoint.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    /**
     * Endpoint pattern with wildcard support.
     * Examples: "/api/users", "/api/users/*", "/api/*"
     *
     * More specific patterns take precedence over wildcards.
     */
    @Column(name = "endpoint_pattern", nullable = false)
    private String endpointPattern;

    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     * Use "*" for all methods.
     */
    @Column(name = "http_method", nullable = false)
    private String httpMethod = "*";

    /**
     * Maximum number of requests allowed in the time window.
     */
    @Column(name = "max_requests", nullable = false)
    private Integer maxRequests;

    /**
     * Time window in seconds.
     * Example: 60 for 1 minute, 3600 for 1 hour
     */
    @Column(name = "window_seconds", nullable = false)
    private Integer windowSeconds;

    /**
     * Rate limiting algorithm to use.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Algorithm algorithm = Algorithm.TOKEN_BUCKET;

    /**
     * For TOKEN_BUCKET algorithm: tokens per second refill rate.
     * For others: not used.
     *
     * Example: If maxRequests=100 and windowSeconds=60,
     * refillRate could be 100/60 = 1.67 tokens per second
     */
    @Column(name = "refill_rate")
    private Double refillRate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * What identifier to use for rate limiting.
     * Default: IP address
     */
    @Column(name = "limit_by", nullable = false)
    @Enumerated(EnumType.STRING)
    private LimitBy limitBy = LimitBy.IP;

    /**
     * Custom header name when limitBy = CUSTOM_HEADER.
     * Example: "X-User-ID", "X-Client-ID"
     */
    @Column(name = "custom_header")
    private String customHeader;

    /**
     * What to use as the rate limit key.
     */
    public enum LimitBy {
        IP,            // Limit by client IP address
        USER_ID,       // Limit by user ID (from header X-User-ID)
        API_KEY,       // Limit by API key itself
        CUSTOM_HEADER  // Limit by custom header value
    }

    /**
     * Rate limiting algorithms.
     *
     * TOKEN_BUCKET: Tokens refill at constant rate, smooth traffic flow
     * SLIDING_WINDOW: Most accurate, uses Redis sorted sets with timestamps
     * FIXED_WINDOW: Simple counter with fixed reset intervals
     * LEAKY_BUCKET: Constant outflow rate, smooths bursts
     */
    public enum Algorithm {
        /**
         * Token Bucket algorithm.
         *
         * Best for: APIs that need smooth traffic flow
         * Pros: Prevents burst traffic, predictable behavior
         * Cons: More complex implementation
         * Use case: Payment APIs, external API calls
         */
        TOKEN_BUCKET,

        /**
         * Sliding Window algorithm.
         *
         * Best for: High accuracy rate limiting
         * Pros: Most accurate, no boundary burst
         * Cons: Higher memory usage (stores timestamps)
         * Use case: Premium tier users, critical endpoints
         */
        SLIDING_WINDOW,

        /**
         * Fixed Window algorithm.
         *
         * Best for: Simple use cases, low latency
         * Pros: Simple, fast, low memory
         * Cons: Allows burst at window boundaries
         * Use case: Free tier, non-critical endpoints
         */
        FIXED_WINDOW,

        /**
         * Leaky Bucket algorithm.
         *
         * Best for: Smoothing bursty traffic
         * Pros: Constant outflow rate, prevents bursts
         * Cons: Can delay legitimate requests
         * Use case: Protecting downstream services
         */
        LEAKY_BUCKET
    }

    /**
     * Calculate refill rate for token bucket algorithm.
     *
     * @return tokens per second
     */
    public Double calculateRefillRate() {
        if (refillRate != null) {
            return refillRate;
        }
        return (double) maxRequests / windowSeconds;
    }
}
