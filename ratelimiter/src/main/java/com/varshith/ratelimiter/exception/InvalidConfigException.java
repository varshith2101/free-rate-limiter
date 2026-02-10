package com.varshith.ratelimiter.exception;

/**
 * Exception thrown when rate limit configuration is invalid.
 *
 * HTTP Status: 400 Bad Request
 */
public class InvalidConfigException extends RuntimeException {

    public InvalidConfigException(String message) {
        super(message);
    }

    public InvalidConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public static InvalidConfigException invalidLimit(int maxRequests) {
        return new InvalidConfigException(
                String.format("Invalid max requests: %d. Must be positive integer.", maxRequests));
    }

    public static InvalidConfigException invalidWindow(int windowSeconds) {
        return new InvalidConfigException(
                String.format("Invalid window size: %d seconds. Must be positive integer.", windowSeconds));
    }

    public static InvalidConfigException tierLimitExceeded(String tier, int current, int max) {
        return new InvalidConfigException(
                String.format("Config limit exceeded for tier %s. Current: %d, Max: %d", tier, current, max));
    }
}
