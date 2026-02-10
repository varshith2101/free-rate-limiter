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
 * Leaky Bucket rate limiting algorithm implementation.
 *
 * Algorithm Overview:
 * - Bucket holds requests like a queue
 * - Requests leak out at constant rate
 * - New requests fill the bucket
 * - If bucket full (capacity reached), reject request
 *
 * Properties:
 * - Constant outflow rate (smooths traffic)
 * - Strict enforcement (no bursts allowed)
 * - Protects downstream services from overload
 *
 * Implementation Details:
 * - Uses Redis Lua script for atomic operations
 * - Stores: {water_level: double, last_leak_time: timestamp}
 * - Calculates leaked water based on elapsed time
 * - Adds 1 unit of water per request
 *
 * Difference from Token Bucket:
 * - Token Bucket: Tokens refill at constant rate (allows bursts up to capacity)
 * - Leaky Bucket: Water leaks at constant rate (strictly enforces rate)
 *
 * Best for: Protecting downstream services, enforcing strict rate limits, smoothing bursty traffic
 *
 * Interview Points:
 * - Leaky vs Token Bucket trade-offs
 * - When to use: strict rate enforcement, protecting backend services
 * - Atomicity via Lua scripts in distributed environment
 */
@Component("LEAKY_BUCKET")
public class LeakyBucketRateLimiter implements RateLimitStrategy {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketRateLimiter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> leakyBucketScript;

    public LeakyBucketRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            RedisScript<List> leakyBucketScript) {
        this.redisTemplate = redisTemplate;
        this.leakyBucketScript = leakyBucketScript;
    }

    /**
     * Check rate limit using Leaky Bucket algorithm.
     *
     * Lua script execution flow:
     * 1. Get current bucket state (water_level, last_leak_time)
     * 2. Calculate elapsed time since last leak
     * 3. Calculate leaked water (leak_rate * elapsed_time)
     * 4. Update water level (current - leaked)
     * 5. Check if bucket can accept 1 more unit
     * 6. If yes, add 1 unit and allow request
     * 7. If no, reject request
     * 8. Update bucket state and TTL
     *
     * @param key Unique identifier
     * @param config Rate limit configuration
     * @return RateLimitResponse with decision and metadata
     */
    @Override
    public RateLimitResponse checkRateLimit(String key, RateLimitConfig config) {
        try {
            String redisKey = "ratelimit:leaky_bucket:" + key;

            // Calculate leak rate (units per second)
            // Leak rate = capacity / window = requests per second that can pass through
            double leakRate = (double) config.getMaxRequests() / config.getWindowSeconds();
            int capacity = config.getMaxRequests();
            long currentTime = System.currentTimeMillis() / 1000;
            int ttl = config.getWindowSeconds() * 2;

            // Execute Lua script atomically
            // KEYS[1] = Redis key
            // ARGV[1] = capacity (max water level)
            // ARGV[2] = leak rate (units/second)
            // ARGV[3] = current timestamp
            // ARGV[4] = TTL
            List<Long> result = (List<Long>) redisTemplate.execute(
                    leakyBucketScript,
                    Collections.singletonList(redisKey),
                    capacity,
                    leakRate,
                    currentTime,
                    ttl
            );

            // Parse result
            // result[0] = allowed (1 or 0)
            // result[1] = remaining capacity
            // result[2] = reset timestamp (when bucket will be empty)
            boolean allowed = result.get(0) == 1;
            int remaining = result.get(1).intValue();
            long resetAt = result.get(2);

            if (log.isDebugEnabled()) {
                log.debug("Leaky Bucket: key={}, allowed={}, remaining={}, resetAt={}",
                        key, allowed, remaining, resetAt);
            }

            if (allowed) {
                return RateLimitResponse.allowed(remaining, capacity, resetAt, getAlgorithmName());
            } else {
                long retryAfter = resetAt - currentTime;
                return RateLimitResponse.builder()
                        .allowed(false)
                        .remaining(0)
                        .limit(capacity)
                        .resetAt(resetAt)
                        .message(String.format("Rate limit exceeded. Bucket full. Try again in %d seconds.", retryAfter))
                        .algorithm(getAlgorithmName())
                        .build();
            }

        } catch (Exception e) {
            log.error("Leaky Bucket rate limit check failed for key: {}", key, e);

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

    @Override
    public void resetRateLimit(String key) {
        try {
            String redisKey = "ratelimit:leaky_bucket:" + key;
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("Reset Leaky Bucket rate limit for key: {}", key);
            } else {
                log.debug("No Leaky Bucket found to reset for key: {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to reset Leaky Bucket rate limit for key: {}", key, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return "LEAKY_BUCKET";
    }
}
