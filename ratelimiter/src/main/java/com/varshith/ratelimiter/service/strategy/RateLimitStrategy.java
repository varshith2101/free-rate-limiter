package com.varshith.ratelimiter.service.strategy;

import com.varshith.ratelimiter.dto.RateLimitResponse;
import com.varshith.ratelimiter.model.RateLimitConfig;

/**
 * Strategy interface for rate limiting algorithms.
 *
 * Implements the Strategy Pattern to allow different rate limiting algorithms
 * to be used interchangeably based on configuration.
 *
 * Implementations:
 * - TokenBucketRateLimiter: Tokens refill at constant rate, smooth traffic flow
 * - SlidingWindowRateLimiter: Accurate window, prevents boundary bursts
 * - FixedWindowRateLimiter: Simple counter with fixed reset intervals
 *
 * All implementations MUST be thread-safe and atomic (using Redis Lua scripts).
 */
public interface RateLimitStrategy {

    /**
     * Check if a request should be allowed based on the rate limit configuration.
     *
     * This method performs the core rate limiting logic:
     * 1. Retrieves current state from Redis
     * 2. Applies algorithm-specific logic
     * 3. Updates state atomically
     * 4. Returns decision with metadata
     *
     * @param key Unique identifier for this rate limit bucket (e.g., "apikey:endpoint")
     * @param config Rate limit configuration containing limits, window size, etc.
     * @return RateLimitResponse containing decision and metadata
     */
    RateLimitResponse checkRateLimit(String key, RateLimitConfig config);

    /**
     * Reset the rate limit for a specific key.
     *
     * Useful for:
     * - Admin operations to clear rate limits
     * - Testing and debugging
     * - Handling special cases (payment received, tier upgrade, etc.)
     *
     * @param key Unique identifier for the rate limit bucket to reset
     */
    void resetRateLimit(String key);

    /**
     * Get the algorithm name for this strategy.
     *
     * Used for logging, metrics, and algorithm selection.
     *
     * @return Algorithm name (TOKEN_BUCKET, SLIDING_WINDOW, or FIXED_WINDOW)
     */
    String getAlgorithmName();
}
