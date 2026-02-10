package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.RateLimitRequest;
import com.varshith.ratelimiter.dto.RateLimitResponse;
import com.varshith.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for rate limiting operations.
 *
 * Main API endpoints for clients to check rate limits.
 */
@RestController
@RequestMapping("/api/v1/ratelimit")
public class RateLimitController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitController.class);

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Check if a request should be allowed based on rate limits.
     *
     * Request Headers:
     * - X-API-Key: API key for authentication
     *
     * Request Body:
     * - endpoint: Endpoint path (e.g., "/api/users")
     * - method: HTTP method (e.g., "GET")
     *
     * Response Headers:
     * - X-RateLimit-Limit: Maximum requests allowed
     * - X-RateLimit-Remaining: Remaining requests
     * - X-RateLimit-Reset: Unix timestamp when limit resets
     *
     * Response Codes:
     * - 200: Request allowed
     * - 429: Rate limit exceeded
     * - 401: Invalid API key
     *
     * @param apiKey API key from header
     * @param request Rate limit check request
     * @return RateLimitResponse with decision
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody RateLimitRequest request) {

        log.debug("Rate limit check request - endpoint: {}, method: {}",
                request.getEndpoint(), request.getMethod());

        // Check rate limit
        RateLimitResponse response = rateLimiterService.checkRateLimit(
                apiKey,
                request.getEndpoint(),
                request.getMethod()
        );

        // Build response with rate limit headers
        ResponseEntity.BodyBuilder responseBuilder;

        if (response.isAllowed()) {
            responseBuilder = ResponseEntity.ok();
        } else {
            responseBuilder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        }

        return responseBuilder
                .header("X-RateLimit-Limit", String.valueOf(response.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(response.getRemaining()))
                .header("X-RateLimit-Reset", String.valueOf(response.getResetAt()))
                .header("X-RateLimit-Algorithm", response.getAlgorithm())
                .body(response);
    }

    /**
     * Get current rate limit status for an API key.
     *
     * Shows all configured rate limits and their current state.
     * Useful for debugging and dashboards.
     *
     * @param apiKey API key from header
     * @return Map of rate limit status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus(
            @RequestHeader("X-API-Key") String apiKey) {

        log.debug("Rate limit status request");

        Map<String, Object> status = rateLimiterService.getRateLimitStatus(apiKey);

        return ResponseEntity.ok(status);
    }

    /**
     * Reset rate limit for an API key and endpoint (admin operation).
     *
     * This is an administrative endpoint that should be protected
     * in production (e.g., require admin API key or authentication).
     *
     * @param apiKey API key
     * @param request Request containing endpoint and method
     * @return Success message
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetRateLimit(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody RateLimitRequest request) {

        log.info("Rate limit reset request - endpoint: {}, method: {}",
                request.getEndpoint(), request.getMethod());

        rateLimiterService.resetRateLimit(
                apiKey,
                request.getEndpoint(),
                request.getMethod()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit reset successfully",
                "endpoint", request.getEndpoint(),
                "method", request.getMethod()
        ));
    }
}
