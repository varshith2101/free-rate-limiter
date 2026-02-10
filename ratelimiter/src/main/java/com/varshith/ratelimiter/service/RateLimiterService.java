package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.RateLimitResponse;
import com.varshith.ratelimiter.exception.ApiKeyNotFoundException;
import com.varshith.ratelimiter.model.ApiKey;
import com.varshith.ratelimiter.model.RateLimitLog;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.repository.ApiKeyRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.RateLimitLogRepository;
import com.varshith.ratelimiter.service.strategy.RateLimitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core rate limiter service.
 *
 * Orchestrates rate limiting logic:
 * 1. Validates API key
 * 2. Finds matching rate limit config
 * 3. Selects appropriate algorithm strategy
 * 4. Executes rate limit check
 * 5. Returns decision
 *
 * This is the main entry point for all rate limiting operations.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitConfigRepository configRepository;
    private final RateLimitLogRepository logRepository;
    private final Map<String, RateLimitStrategy> strategyMap;

    /**
     * Constructor injection with strategy map.
     *
     * Spring automatically injects all RateLimitStrategy beans into a Map
     * where key = bean name (e.g., "TOKEN_BUCKET") and value = strategy instance.
     */
    public RateLimiterService(
            ApiKeyRepository apiKeyRepository,
            RateLimitConfigRepository configRepository,
            RateLimitLogRepository logRepository,
            Map<String, RateLimitStrategy> strategyMap) {
        this.apiKeyRepository = apiKeyRepository;
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.strategyMap = strategyMap;

        log.info("RateLimiterService initialized with {} strategies: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    /**
     * Check rate limit for a request.
     *
     * Process:
     * 1. Validate API key (active, not expired)
     * 2. Find matching rate limit config for endpoint
     * 3. If no config found, use default limits
     * 4. Select algorithm strategy
     * 5. Execute rate limit check
     * 6. Return response with decision
     *
     * @param apiKeyString API key from request header
     * @param endpoint     Endpoint path (e.g., "/api/users")
     * @param method       HTTP method (e.g., "GET")
     * @return RateLimitResponse with decision and metadata
     * @throws ApiKeyNotFoundException if API key invalid or expired
     */
    public RateLimitResponse checkRateLimit(String apiKeyString, String endpoint, String method) {
        log.debug("Checking rate limit - apiKey: {}, endpoint: {}, method: {}",
                maskApiKey(apiKeyString), endpoint, method);

        // Step 1: Validate API key
        ApiKey apiKey = validateApiKey(apiKeyString);
        UUID tenantId = apiKey.getTenant().getId();

        // Step 2: Find matching config
        RateLimitConfig config = findMatchingConfig(tenantId, endpoint, method);

        if (config == null) {
            log.debug("No rate limit config found for tenant: {}, endpoint: {}, method: {}. Skipping.",
                tenantId, endpoint, method);
            return RateLimitResponse.builder()
                .allowed(true)
                .remaining(0)
                .limit(0)
                .resetAt(0)
                .message("No rate limit config matched")
                .algorithm("NONE")
                .matchedPattern(null)
                .build();
        }

        // Step 3: Build Redis key
        String rateLimitKey = buildRateLimitKey(apiKeyString, endpoint, method);

        // Step 4: Select and execute strategy
        RateLimitStrategy strategy = selectStrategy(config.getAlgorithm());
        RateLimitResponse response = strategy.checkRateLimit(rateLimitKey, config);

        // Step 5: Add metadata
        response.setMatchedPattern(config.getEndpointPattern());

        persistLog(apiKey, config, endpoint, method, response);

        log.info("Rate limit check complete - apiKey: {}, endpoint: {}, allowed: {}, remaining: {}",
                maskApiKey(apiKeyString), endpoint, response.isAllowed(), response.getRemaining());

        return response;
    }

    private void persistLog(ApiKey apiKey, RateLimitConfig config, String endpoint, String method, RateLimitResponse response) {
        RateLimitLog logEntry = new RateLimitLog();
        logEntry.setTenant(apiKey.getTenant());
        logEntry.setEndpoint(config.getEndpoint());
        logEntry.setConfig(config);
        logEntry.setTimestamp(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        logEntry.setIdentifierType(config.getLimitBy());
        logEntry.setClientIdentifier(resolveIdentifier(config.getLimitBy(), apiKey));
        logEntry.setAllowed(response.isAllowed());
        logEntry.setMaxRequests(response.getLimit());
        logEntry.setCurrentCount(Math.max(0, response.getLimit() - response.getRemaining()));
        logEntry.setAlgorithm(config.getAlgorithm());
        logEntry.setHttpMethod(method);
        logEntry.setRequestPath(endpoint);

        logRepository.save(logEntry);
    }

    private String resolveIdentifier(RateLimitConfig.LimitBy limitBy, ApiKey apiKey) {
        if (limitBy == RateLimitConfig.LimitBy.API_KEY) {
            return apiKey.getApiKey();
        }
        return "unknown";
    }

    /**
     * Get rate limit status for an API key across all endpoints.
     *
     * Useful for dashboards and debugging.
     *
     * @param apiKeyString API key
     * @return Map of endpoint patterns to their current status
     */
    public Map<String, Object> getRateLimitStatus(String apiKeyString) {
        ApiKey apiKey = validateApiKey(apiKeyString);
        UUID tenantId = apiKey.getTenant().getId();

        List<RateLimitConfig> configs = configRepository.findActiveByTenantId(tenantId);

        Map<String, Object> status = new HashMap<>();
        status.put("apiKey", maskApiKey(apiKeyString));
        status.put("tenant", apiKey.getTenant().getName());
        status.put("tier", apiKey.getTenant().getTier());
        status.put("configCount", configs.size());
        status.put("configs", configs.stream().map(config -> {
            Map<String, Object> configInfo = new HashMap<>();
            configInfo.put("pattern", config.getEndpointPattern());
            configInfo.put("method", config.getHttpMethod());
            configInfo.put("limit", config.getMaxRequests());
            configInfo.put("windowSeconds", config.getWindowSeconds());
            configInfo.put("algorithm", config.getAlgorithm());
            return configInfo;
        }).toList());

        return status;
    }

    /**
     * Reset rate limit for a specific API key and endpoint.
     *
     * Admin operation to clear rate limits.
     *
     * @param apiKeyString API key
     * @param endpoint     Endpoint path
     * @param method       HTTP method
     */
    public void resetRateLimit(String apiKeyString, String endpoint, String method) {
        ApiKey apiKey = validateApiKey(apiKeyString);
        UUID tenantId = apiKey.getTenant().getId();

        RateLimitConfig config = findMatchingConfig(tenantId, endpoint, method);
        if (config == null) {
            log.warn("No config found to reset for endpoint: {}", endpoint);
            return;
        }

        String rateLimitKey = buildRateLimitKey(apiKeyString, endpoint, method);
        RateLimitStrategy strategy = selectStrategy(config.getAlgorithm());
        strategy.resetRateLimit(rateLimitKey);

        log.info("Rate limit reset - apiKey: {}, endpoint: {}", maskApiKey(apiKeyString), endpoint);
    }

    /**
     * Validate API key: exists, active, not expired.
     *
     * @param apiKeyString API key from request
     * @return ApiKey entity
     * @throws ApiKeyNotFoundException if invalid
     */
    private ApiKey validateApiKey(String apiKeyString) {
        if (apiKeyString == null || apiKeyString.isBlank()) {
            throw ApiKeyNotFoundException.forKey("empty");
        }

        ApiKey apiKey = apiKeyRepository.findActiveApiKey(apiKeyString, LocalDateTime.now())
                .orElseThrow(() -> ApiKeyNotFoundException.forKey(apiKeyString));

        return apiKey;
    }

    /**
     * Find matching rate limit config for tenant + endpoint + method.
     *
     * Matching logic:
     * 1. Try exact match first
     * 2. Try wildcard patterns (most specific first)
     * 3. Return null if no match
     *
     * @param tenantId Tenant ID
     * @param endpoint Endpoint path
     * @param method   HTTP method
     * @return Matching config or null
     */
    private RateLimitConfig findMatchingConfig(UUID tenantId, String endpoint, String method) {
        // Try exact match first
        var exactMatch = configRepository.findExactMatch(tenantId, endpoint, method);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        // Try wildcard patterns (ordered by specificity)
        List<RateLimitConfig> matchingConfigs = configRepository.findMatchingConfigs(tenantId, endpoint, method);
        if (!matchingConfigs.isEmpty()) {
            return matchingConfigs.get(0); // Most specific match
        }

        return null;
    }

    /**
     * Get default rate limit config when no tenant-specific config exists.
     *
     * Defaults based on tier:
     * - FREE: 100 requests/minute, Fixed Window
     * - PRO: 1000 requests/minute, Token Bucket
     * - ENTERPRISE: 10000 requests/minute, Sliding Window
     *
     * @param tenant Tenant
     * @return Default config (not persisted)
     */
    private RateLimitConfig getDefaultConfig(com.varshith.ratelimiter.model.Tenant tenant) {
        RateLimitConfig config = new RateLimitConfig();
        config.setTenant(tenant);
        config.setEndpointPattern("*");
        config.setHttpMethod("*");

        switch (tenant.getTier()) {
            case FREE:
                config.setMaxRequests(100);
                config.setWindowSeconds(60);
                config.setAlgorithm(RateLimitConfig.Algorithm.FIXED_WINDOW);
                break;
            case PRO:
                config.setMaxRequests(1000);
                config.setWindowSeconds(60);
                config.setAlgorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET);
                break;
            case ENTERPRISE:
                config.setMaxRequests(10000);
                config.setWindowSeconds(60);
                config.setAlgorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW);
                break;
        }

        return config;
    }

    /**
     * Build Redis key for rate limiting.
     *
     * Format: apiKey:endpoint:method
     * Example: rlim_abc123.../api/users:GET
     *
     * @param apiKey   API key
     * @param endpoint Endpoint path
     * @param method   HTTP method
     * @return Redis key
     */
    private String buildRateLimitKey(String apiKey, String endpoint, String method) {
        return String.format("%s:%s:%s", apiKey, endpoint, method);
    }

    /**
     * Select rate limiting strategy based on algorithm.
     *
     * @param algorithm Algorithm enum
     * @return Strategy implementation
     */
    private RateLimitStrategy selectStrategy(RateLimitConfig.Algorithm algorithm) {
        RateLimitStrategy strategy = strategyMap.get(algorithm.name());

        if (strategy == null) {
            log.warn("Strategy not found for algorithm: {}, falling back to TOKEN_BUCKET", algorithm);
            strategy = strategyMap.get("TOKEN_BUCKET");
        }

        return strategy;
    }

    /**
     * Mask API key for logging.
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 12) {
            return "***";
        }
        return apiKey.substring(0, 12) + "***";
    }
}
