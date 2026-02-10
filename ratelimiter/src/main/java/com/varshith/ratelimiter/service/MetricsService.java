package com.varshith.ratelimiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics service for Prometheus monitoring.
 *
 * Tracks:
 * - Rate limiter request counts (allowed/denied)
 * - Rate limit violations by tenant and endpoint
 * - Check duration latency (p50, p95, p99)
 * - Active tenants gauge
 *
 * Metrics are exposed at /actuator/prometheus
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter allowedRequestsCounter;
    private final Counter deniedRequestsCounter;

    // Timer for latency tracking
    private final Timer rateLimitCheckTimer;

    // Gauge for active tenants
    private final AtomicInteger activeTenants = new AtomicInteger(0);

    // Map to track per-tenant counters
    private final ConcurrentHashMap<String, Counter> tenantViolationCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.allowedRequestsCounter = Counter.builder("rate_limiter_requests_allowed_total")
                .description("Total number of requests allowed by rate limiter")
                .register(meterRegistry);

        this.deniedRequestsCounter = Counter.builder("rate_limiter_requests_denied_total")
                .description("Total number of requests denied by rate limiter")
                .register(meterRegistry);

        // Initialize timer for latency tracking
        this.rateLimitCheckTimer = Timer.builder("rate_limiter_check_duration_seconds")
                .description("Time taken to check rate limits")
                .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
                .register(meterRegistry);

        // Register gauge for active tenants
        Gauge.builder("rate_limiter_active_tenants", activeTenants, AtomicInteger::get)
                .description("Number of active tenants")
                .register(meterRegistry);
    }

    /**
     * Record an allowed request.
     *
     * @param tenantId Tenant ID
     * @param endpoint Endpoint
     * @param algorithm Algorithm used
     */
    public void recordAllowedRequest(String tenantId, String endpoint, String algorithm) {
        allowedRequestsCounter.increment();

        // Record with tags for detailed filtering
        Counter.builder("rate_limiter_requests_total")
                .tag("tenant", tenantId)
                .tag("endpoint", endpoint)
                .tag("algorithm", algorithm)
                .tag("result", "allowed")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record a denied request (rate limit violation).
     *
     * @param tenantId Tenant ID
     * @param endpoint Endpoint
     * @param algorithm Algorithm used
     */
    public void recordDeniedRequest(String tenantId, String endpoint, String algorithm) {
        deniedRequestsCounter.increment();

        // Record violation
        Counter.builder("rate_limiter_requests_total")
                .tag("tenant", tenantId)
                .tag("endpoint", endpoint)
                .tag("algorithm", algorithm)
                .tag("result", "denied")
                .register(meterRegistry)
                .increment();

        // Track per-tenant violations
        String tenantKey = "tenant:" + tenantId;
        tenantViolationCounters.computeIfAbsent(tenantKey, k ->
                Counter.builder("rate_limiter_violations_total")
                        .tag("tenant", tenantId)
                        .description("Rate limit violations by tenant")
                        .register(meterRegistry)
        ).increment();
    }

    /**
     * Record rate limit check latency.
     *
     * Usage:
     * <pre>
     * Timer.Sample sample = Timer.start(meterRegistry);
     * // ... perform rate limit check ...
     * metricsService.recordCheckDuration(sample);
     * </pre>
     *
     * @param sample Timer sample
     */
    public void recordCheckDuration(Timer.Sample sample) {
        sample.stop(rateLimitCheckTimer);
    }

    /**
     * Update active tenants count.
     *
     * @param count Number of active tenants
     */
    public void updateActiveTenants(int count) {
        activeTenants.set(count);
    }

    /**
     * Record Redis operation metrics.
     *
     * @param operation Operation type (get, set, eval)
     * @param success Whether operation succeeded
     */
    public void recordRedisOperation(String operation, boolean success) {
        Counter.builder("rate_limiter_redis_operations_total")
                .tag("operation", operation)
                .tag("result", success ? "success" : "failure")
                .description("Redis operations performed by rate limiter")
                .register(meterRegistry)
                .increment();
    }
}
