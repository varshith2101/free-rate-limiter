package com.varshith.ratelimiter.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for generating secure API keys.
 *
 * API Key Format: rlim_<32_random_characters>
 * Example: rlim_A8f3K9mP2xQ7nR4sT6vW1yZ5bC0dE2gH
 *
 * Uses SecureRandom for cryptographically strong random values.
 */
public class ApiKeyGenerator {

    private static final String PREFIX = "rlim_";
    private static final int KEY_LENGTH = 32; // 32 characters after prefix
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Base62 alphabet (alphanumeric only, no special chars for easier copying)
    private static final String BASE62_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Generate a new API key with prefix.
     *
     * @return Newly generated API key (e.g., "rlim_A8f3K9mP2xQ7nR4sT6vW1yZ5bC0dE2gH")
     */
    public static String generate() {
        return PREFIX + generateRandomString(KEY_LENGTH);
    }

    /**
     * Generate random alphanumeric string of specified length.
     *
     * Uses Base62 encoding (A-Z, a-z, 0-9) for URL-safe keys.
     *
     * @param length Length of random string to generate
     * @return Random string
     */
    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(BASE62_ALPHABET.length());
            sb.append(BASE62_ALPHABET.charAt(randomIndex));
        }

        return sb.toString();
    }

    /**
     * Alternative: Generate using Base64 encoding.
     * Produces URL-safe Base64 string without padding.
     *
     * @return API key with Base64-encoded random bytes
     */
    public static String generateBase64() {
        byte[] randomBytes = new byte[24]; // 24 bytes = 32 chars in Base64
        SECURE_RANDOM.nextBytes(randomBytes);

        String base64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return PREFIX + base64;
    }

    /**
     * Validate API key format.
     *
     * @param apiKey API key to validate
     * @return true if format is valid
     */
    public static boolean isValidFormat(String apiKey) {
        if (apiKey == null) {
            return false;
        }

        // Check prefix
        if (!apiKey.startsWith(PREFIX)) {
            return false;
        }

        // Check length (prefix + 32 chars)
        if (apiKey.length() != PREFIX.length() + KEY_LENGTH) {
            return false;
        }

        // Check if remaining chars are alphanumeric
        String keyPart = apiKey.substring(PREFIX.length());
        return keyPart.matches("[A-Za-z0-9]+");
    }

    /**
     * Mask API key for logging (show only first 12 characters).
     *
     * @param apiKey API key to mask
     * @return Masked key (e.g., "rlim_A8f3K9***")
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.length() <= 12) {
            return "***";
        }
        return apiKey.substring(0, 12) + "***";
    }
}
