package com.varshith.ratelimiter.exception;

/**
 * Exception thrown when rate limit is exceeded.
 *
 * HTTP Status: 429 Too Many Requests
 */
public class RateLimitExceededException extends RuntimeException {

    private final long resetAt;
    private final int limit;

    public RateLimitExceededException(String message, int limit, long resetAt) {
        super(message);
        this.limit = limit;
        this.resetAt = resetAt;
    }

    public long getResetAt() {
        return resetAt;
    }

    public int getLimit() {
        return limit;
    }

    public static RateLimitExceededException forEndpoint(String endpoint, int limit, long resetAt) {
        long secondsUntilReset = resetAt - System.currentTimeMillis() / 1000;
        String message = String.format(
                "Rate limit exceeded for endpoint: %s. Limit: %d requests. Try again in %d seconds.",
                endpoint, limit, Math.max(1, secondsUntilReset));
        return new RateLimitExceededException(message, limit, resetAt);
    }
}
