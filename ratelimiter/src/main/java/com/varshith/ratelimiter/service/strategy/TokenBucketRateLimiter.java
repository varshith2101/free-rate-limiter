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
 * Token Bucket rate limiting algorithm implementation.
 *
 * Algorithm Overview:
 * - Bucket starts with max capacity of tokens
 * - Tokens are consumed on each request (1 token per request)
 * - Tokens refill at constant rate (e.g., 100 tokens/60 seconds = 1.67 tokens/sec)
 * - Request allowed if at least 1 token available
 *
 * Properties:
 * - Smooth traffic flow (no sudden bursts)
 * - Handles bursty traffic gracefully (up to bucket capacity)
 * - Industry standard for API rate limiting
 *
 * Implementation Details:
 * - Uses Redis Lua script for atomic operations
 * - Stores: {tokens: double, last_refill: timestamp}
 * - Calculates tokens to add based on elapsed time
 * - Updates bucket state atomically
 *
 * Best for: Payment APIs, external API calls, production endpoints
 *
 * Interview Points:
 * - Why Lua? Prevents race conditions in distributed systems
 * - Why not local counter? Service is stateless, Redis is source of truth
 * - Atomicity: Multiple instances can safely check same limit
 */
@Component("TOKEN_BUCKET")
public class TokenBucketRateLimiter implements RateLimitStrategy {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    /**
     * Constructor injection for dependencies.
     * Spring Boot 4.0+ automatically wires beans without @Autowired annotation.
     */
    public TokenBucketRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            RedisScript<List> tokenBucketScript) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
    }

    /**
     * Check rate limit using Token Bucket algorithm.
     *
     * Lua script execution flow:
     * 1. Get current bucket state (tokens, last_refill)
     * 2. Calculate elapsed time since last refill
     * 3. Add tokens based on refill rate * elapsed time
     * 4. Cap tokens at max capacity
     * 5. Check if >= 1 token available
     * 6. Consume 1 token if available
     * 7. Update bucket state and TTL
     * 8. Return result
     *
     * @param key Unique identifier (e.g., "ratelimit:bucket:apikey123:/api/users")
     * @param config Rate limit configuration
     * @return RateLimitResponse with decision and metadata
     */
    @Override
    public RateLimitResponse checkRateLimit(String key, RateLimitConfig config) {
        try {
            // Prepare Redis key with algorithm prefix for easy debugging
            String redisKey = "ratelimit:token_bucket:" + key;

            // Calculate refill rate (tokens per second)
            double refillRate = config.calculateRefillRate();
            int maxCapacity = config.getMaxRequests();
            long currentTime = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
            int ttl = config.getWindowSeconds() * 2; // TTL = 2x window for safety

            // Execute Lua script atomically
            // KEYS[1] = Redis key
            // ARGV[1] = max capacity
            // ARGV[2] = refill rate (tokens/second)
            // ARGV[3] = current timestamp
            // ARGV[4] = TTL
            List<Long> result = (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(redisKey),
                    maxCapacity,
                    refillRate,
                    currentTime,
                    ttl
            );

            // Parse result from Lua script
            // result[0] = allowed (1 or 0)
            // result[1] = remaining tokens (floored to int)
            // result[2] = reset timestamp
            boolean allowed = result.get(0) == 1;
            int remaining = result.get(1).intValue();
            long resetAt = result.get(2);

            // Log the decision for debugging and analytics
            if (log.isDebugEnabled()) {
                log.debug("Token Bucket: key={}, allowed={}, remaining={}, resetAt={}",
                        key, allowed, remaining, resetAt);
            }

            // Build response
            if (allowed) {
                return RateLimitResponse.allowed(remaining, maxCapacity, resetAt, getAlgorithmName());
            } else {
                return RateLimitResponse.rejected(maxCapacity, resetAt, getAlgorithmName());
            }

        } catch (Exception e) {
            // On Redis failure, fail open (allow request) or fail closed (deny request)
            // For production, this should be configurable per tenant
            log.error("Token Bucket rate limit check failed for key: {}", key, e);

            // Fail-open strategy: allow request but log error
            // In production, consider circuit breaker pattern
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
     * Reset rate limit by deleting the bucket from Redis.
     *
     * Useful for:
     * - Admin operations (clear user's rate limit)
     * - Testing and debugging
     * - Tier upgrades (reset to allow more requests immediately)
     *
     * @param key Unique identifier for the bucket
     */
    @Override
    public void resetRateLimit(String key) {
        try {
            String redisKey = "ratelimit:token_bucket:" + key;
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("Reset Token Bucket rate limit for key: {}", key);
            } else {
                log.debug("No Token Bucket found to reset for key: {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to reset Token Bucket rate limit for key: {}", key, e);
        }
    }

    /**
     * Get algorithm name for this strategy.
     *
     * @return "TOKEN_BUCKET"
     */
    @Override
    public String getAlgorithmName() {
        return "TOKEN_BUCKET";
    }
}
