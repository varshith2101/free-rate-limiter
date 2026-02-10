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
import java.util.UUID;

/**
 * Sliding Window rate limiting algorithm implementation.
 *
 * Algorithm Overview:
 * - Uses Redis sorted set with timestamps as scores
 * - Each request is stored with its timestamp
 * - On each check, removes expired entries and counts remaining
 * - Most accurate rate limiting algorithm
 *
 * Properties:
 * - No boundary burst problem (unlike fixed window)
 * - True sliding window calculation
 * - Higher memory usage (stores timestamp for each request)
 * - More accurate than token bucket for strict limits
 *
 * Implementation Details:
 * - Uses Redis sorted set (ZSET)
 * - Score = request timestamp in milliseconds
 * - Member = unique request ID
 * - Lua script ensures atomicity
 *
 * Best for: Premium tier users, strict compliance requirements, critical endpoints
 *
 * Interview Points:
 * - Why sorted set? Efficient range queries by timestamp
 * - ZREMRANGEBYSCORE: O(log(N)+M) where M is removed elements
 * - ZCARD: O(1) operation to count elements
 * - Trade-off: More memory for better accuracy
 */
@Component("SLIDING_WINDOW")
public class SlidingWindowRateLimiter implements RateLimitStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> slidingWindowScript;

    public SlidingWindowRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            RedisScript<List> slidingWindowScript) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = slidingWindowScript;
    }

    /**
     * Check rate limit using Sliding Window algorithm.
     *
     * Lua script execution flow:
     * 1. Calculate window start time (now - window_size)
     * 2. Remove all entries with timestamp < window_start (ZREMRANGEBYSCORE)
     * 3. Count remaining entries in window (ZCARD)
     * 4. If count < max_requests:
     *    - Add current request with timestamp as score (ZADD)
     *    - Allow request
     * 5. Else: Reject request
     * 6. Set TTL on sorted set
     * 7. Return result
     *
     * @param key Unique identifier
     * @param config Rate limit configuration
     * @return RateLimitResponse with decision and metadata
     */
    @Override
    public RateLimitResponse checkRateLimit(String key, RateLimitConfig config) {
        try {
            String redisKey = "ratelimit:sliding_window:" + key;

            int windowSeconds = config.getWindowSeconds();
            int maxRequests = config.getMaxRequests();
            long now = System.currentTimeMillis(); // Milliseconds for precision
            String requestId = UUID.randomUUID().toString();

            // Execute Lua script atomically
            // KEYS[1] = Redis sorted set key
            // ARGV[1] = window size in seconds
            // ARGV[2] = max requests allowed
            // ARGV[3] = current timestamp (milliseconds)
            // ARGV[4] = unique request ID
            List<Long> result = (List<Long>) redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(redisKey),
                    windowSeconds,
                    maxRequests,
                    now,
                    requestId
            );

            // Parse result
            // result[0] = allowed (1 or 0)
            // result[1] = remaining requests
            // result[2] = window reset time (seconds)
            boolean allowed = result.get(0) == 1;
            int remaining = result.get(1).intValue();
            long resetAt = result.get(2);

            if (log.isDebugEnabled()) {
                log.debug("Sliding Window: key={}, allowed={}, remaining={}, resetAt={}",
                        key, allowed, remaining, resetAt);
            }

            if (allowed) {
                return RateLimitResponse.allowed(remaining, maxRequests, resetAt, getAlgorithmName());
            } else {
                return RateLimitResponse.rejected(maxRequests, resetAt, getAlgorithmName());
            }

        } catch (Exception e) {
            log.error("Sliding Window rate limit check failed for key: {}", key, e);

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
     * Reset rate limit by deleting the sorted set from Redis.
     *
     * @param key Unique identifier
     */
    @Override
    public void resetRateLimit(String key) {
        try {
            String redisKey = "ratelimit:sliding_window:" + key;
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("Reset Sliding Window rate limit for key: {}", key);
            } else {
                log.debug("No Sliding Window found to reset for key: {}", key);
            }
        } catch (Exception e) {
            log.error("Failed to reset Sliding Window rate limit for key: {}", key, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return "SLIDING_WINDOW";
    }
}
