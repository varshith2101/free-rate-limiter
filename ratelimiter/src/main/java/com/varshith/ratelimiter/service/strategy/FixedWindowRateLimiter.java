package com.varshith.ratelimiter.service.strategy;

import com.varshith.ratelimiter.dto.RateLimitResponse;
import com.varshith.ratelimiter.model.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fixed Window rate limiting algorithm implementation.
 *
 * Algorithm Overview:
 * - Simple counter that increments on each request
 * - Window resets at fixed intervals (e.g., every 60 seconds)
 * - Request allowed if counter < max_requests
 *
 * Properties:
 * - Simplest implementation
 * - Lowest memory usage (single counter per key)
 * - Fastest performance (O(1) operations)
 * - Boundary burst problem: Can allow 2x traffic at window boundaries
 *
 * Example of boundary burst:
 * - Window: 60 seconds, Limit: 100 requests
 * - 100 requests at 59th second (allowed)
 * - Window resets at 60th second
 * - 100 more requests at 61st second (allowed)
 * - Result: 200 requests in 2 seconds!
 *
 * Implementation Details:
 * - Uses Redis counter with TTL
 * - INCR for atomic increment
 * - TTL set only on first request
 * - Lua script ensures atomicity
 *
 * Best for: Free tier, non-critical endpoints, low latency requirements
 *
 * Interview Points:
 * - Simplicity vs. accuracy trade-off
 * - When is "good enough" actually good enough?
 * - Cost-benefit: Saves memory and latency for 95% of use cases
 */
@Component("FIXED_WINDOW")
public class FixedWindowRateLimiter implements RateLimitStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> fixedWindowScript;

    public FixedWindowRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            RedisScript<List> fixedWindowScript) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = fixedWindowScript;
    }

    /**
     * Check rate limit using Fixed Window algorithm.
     *
     * Lua script execution flow:
     * 1. GET current counter value
     * 2. If counter < max_requests:
     *    - INCR counter
     *    - If first request (count was 0): EXPIRE key with TTL
     *    - Allow request
     * 3. Else: Reject request
     * 4. Get TTL to calculate reset time
     * 5. Return result
     *
     * Why Lua?
     * - Without Lua: Race condition between GET, INCR, EXPIRE
     * - Example: Two requests at same time could both read count=99, both increment to 100
     * - With Lua: All operations execute atomically
     *
     * @param key Unique identifier
     * @param config Rate limit configuration
     * @return RateLimitResponse with decision and metadata
     */
    @Override
    public RateLimitResponse checkRateLimit(String key, RateLimitConfig config) {
        try {
            String redisKey = "ratelimit:fixed_window:" + key;

            int maxRequests = config.getMaxRequests();
            int windowSeconds = config.getWindowSeconds();

            // Execute Lua script atomically
            // KEYS[1] = Redis counter key
            // ARGV[1] = max requests allowed
            // ARGV[2] = window size in seconds
            // ARGV[3] = current timestamp (seconds)
            long now = System.currentTimeMillis() / 1000;
            List<Long> result = (List<Long>) redisTemplate.execute(
                    fixedWindowScript,
                    Collections.singletonList(redisKey),
                    maxRequests,
                    windowSeconds,
                    now
            );

            // Parse result
            // result[0] = allowed (1 or 0)
            // result[1] = remaining requests
            // result[2] = reset timestamp (seconds)
            boolean allowed = result.get(0) == 1;
            int remaining = result.get(1).intValue();
            long resetAt = result.get(2);

            if (log.isDebugEnabled()) {
                log.debug("Fixed Window: key={}, allowed={}, remaining={}, resetAt={}",
                        key, allowed, remaining, resetAt);
            }

            if (allowed) {
                return RateLimitResponse.allowed(remaining, maxRequests, resetAt, getAlgorithmName());
            } else {
                return RateLimitResponse.rejected(maxRequests, resetAt, getAlgorithmName());
            }

        } catch (Exception e) {
            log.error("Fixed Window rate limit check failed for key: {}", key, e);

            // Fail-open strategy
            return RateLimitResponse.builder()
                    .allowed(true)
                    .remaining(config.getMaxRequests())
                    .limit(config.getMaxRequests())
                    .resetAt(System.currentTimeMillis() / 1000 + config.getWindowSeconds())
                    .message("Rate limit check failed, request allowed (fail-open)")
                    .algorithm(getAlgorithmName())
                    .build();
        }
    }

    /**
     * Reset rate limit by deleting the counter from Redis.
     *
     * @param key Unique identifier
     */
    @Override
    public void resetRateLimit(String key) {
        try {
            String redisKey = "ratelimit:fixed_window:" + key;
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("Reset Fixed Window rate limit for key: {}", key);
            } else {
                log.debug("No Fixed Window found to reset for key: {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to reset Fixed Window rate limit for key: {}", key, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return "FIXED_WINDOW";
    }
}
