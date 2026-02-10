package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for rate limit check results.
 *
 * Contains decision on whether request is allowed and metadata for client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResponse {

    /**
     * Whether the request is allowed.
     * true = proceed with request, false = reject with 429
     */
    private boolean allowed;

    /**
     * Number of requests remaining in current window.
     * Helps clients implement client-side throttling.
     */
    private int remaining;

    /**
     * Maximum requests allowed in the window.
     */
    private int limit;

    /**
     * Timestamp when the rate limit will reset (Unix epoch seconds).
     * Clients can use this to know when to retry.
     */
    private long resetAt;

    /**
     * Human-readable message explaining the decision.
     * Examples:
     * - "Request allowed"
     * - "Rate limit exceeded. Try again in 45 seconds."
     */
    private String message;

    /**
     * Algorithm used for this rate limit check.
     * Useful for debugging and analytics.
     */
    private String algorithm;

    /**
     * Endpoint pattern that matched.
     * Useful for debugging wildcard patterns.
     */
    private String matchedPattern;

    /**
     * Create a success response (request allowed).
     */
    public static RateLimitResponse allowed(int remaining, int limit, long resetAt, String algorithm) {
        return RateLimitResponse.builder()
                .allowed(true)
                .remaining(remaining)
                .limit(limit)
                .resetAt(resetAt)
                .message("Request allowed")
                .algorithm(algorithm)
                .build();
    }

    /**
     * Create a rejected response (rate limit exceeded).
     */
    public static RateLimitResponse rejected(int limit, long resetAt, String algorithm) {
        long secondsUntilReset = resetAt - Instant.now().getEpochSecond();
        String message = String.format("Rate limit exceeded. Try again in %d seconds.",
                Math.max(1, secondsUntilReset));

        return RateLimitResponse.builder()
                .allowed(false)
                .remaining(0)
                .limit(limit)
                .resetAt(resetAt)
                .message(message)
                .algorithm(algorithm)
                .build();
    }
}
