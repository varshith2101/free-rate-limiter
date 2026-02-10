package com.varshith.ratelimiter.exception;

/**
 * Exception thrown when an API key is not found or is invalid.
 *
 * HTTP Status: 401 Unauthorized
 */
public class ApiKeyNotFoundException extends RuntimeException {

    public ApiKeyNotFoundException(String message) {
        super(message);
    }

    public ApiKeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ApiKeyNotFoundException forKey(String apiKey) {
        return new ApiKeyNotFoundException(
                String.format("API key not found or inactive: %s", maskApiKey(apiKey)));
    }

    /**
     * Mask API key for logging (show only first 8 chars).
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 8) + "***";
    }
}
