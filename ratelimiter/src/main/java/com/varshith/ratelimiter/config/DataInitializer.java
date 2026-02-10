package com.varshith.ratelimiter.config;

import com.varshith.ratelimiter.model.ApiKey;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.repository.ApiKeyRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import com.varshith.ratelimiter.util.ApiKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * Data initializer to populate sample data on startup.
 *
 * Creates:
 * - 3 sample tenants (one for each tier)
 * - 2-3 API keys per tenant
 * - Multiple rate limit configs with different algorithms
 *
 * Makes it easy to test the application immediately after startup.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Initialize sample data on application startup.
     *
     * Uses CommandLineRunner to execute after Spring context is loaded.
     */
    @Bean
    public CommandLineRunner initializeData(
            TenantRepository tenantRepository,
            ApiKeyRepository apiKeyRepository,
            RateLimitConfigRepository configRepository) {

        return args -> {
            log.info("=".repeat(60));
            log.info("Initializing sample data for rate limiter...");
            log.info("=".repeat(60));

            // Check if data already exists
            if (tenantRepository.count() > 0) {
                log.info("Data already exists, skipping initialization");
                return;
            }

            // ==================== Create Tenants ====================

            Tenant freeTenant = createTenant(tenantRepository, "Demo Corp (FREE)", Tenant.TenantTier.FREE);
            Tenant proTenant = createTenant(tenantRepository, "Acme Inc (PRO)", Tenant.TenantTier.PRO);
            Tenant enterpriseTenant = createTenant(tenantRepository, "BigTech Ltd (ENTERPRISE)", Tenant.TenantTier.ENTERPRISE);

            // ==================== Create API Keys ====================

            // Free tenant - 2 keys
            String freeKey1 = createApiKey(apiKeyRepository, freeTenant, null);
            String freeKey2 = createApiKey(apiKeyRepository, freeTenant, LocalDateTime.now().plusYears(1));

            // Pro tenant - 2 keys
            String proKey1 = createApiKey(apiKeyRepository, proTenant, null);
            String proKey2 = createApiKey(apiKeyRepository, proTenant, LocalDateTime.now().plusYears(1));

            // Enterprise tenant - 3 keys
            String enterpriseKey1 = createApiKey(apiKeyRepository, enterpriseTenant, null);
            String enterpriseKey2 = createApiKey(apiKeyRepository, enterpriseTenant, null);
            String enterpriseKey3 = createApiKey(apiKeyRepository, enterpriseTenant, LocalDateTime.now().plusYears(2));

            // ==================== Create Rate Limit Configs ====================

            // Free Tier - Fixed Window (simple, fast)
            createConfig(configRepository, freeTenant, "/api/users", "GET",
                    100, 60, RateLimitConfig.Algorithm.FIXED_WINDOW, null);
            createConfig(configRepository, freeTenant, "/api/posts", "POST",
                    20, 60, RateLimitConfig.Algorithm.FIXED_WINDOW, null);

            // Pro Tier - Token Bucket (smooth traffic)
            createConfig(configRepository, proTenant, "/api/users/*", "GET",
                    1000, 60, RateLimitConfig.Algorithm.TOKEN_BUCKET, 16.67);
            createConfig(configRepository, proTenant, "/api/payments", "POST",
                    100, 60, RateLimitConfig.Algorithm.TOKEN_BUCKET, 1.67);
            createConfig(configRepository, proTenant, "/api/analytics/*", "*",
                    500, 60, RateLimitConfig.Algorithm.TOKEN_BUCKET, 8.33);

            // Enterprise Tier - Sliding Window (most accurate)
            createConfig(configRepository, enterpriseTenant, "/api/*", "*",
                    10000, 60, RateLimitConfig.Algorithm.SLIDING_WINDOW, null);
            createConfig(configRepository, enterpriseTenant, "/api/critical/*", "POST",
                    5000, 60, RateLimitConfig.Algorithm.SLIDING_WINDOW, null);

            // ==================== Print Summary ====================

            log.info("=".repeat(60));
            log.info("Sample data created successfully!");
            log.info("=".repeat(60));
            log.info("");
            log.info("FREE TIER - Demo Corp:");
            log.info("  API Key 1: {}", freeKey1);
            log.info("  API Key 2: {} (expires in 1 year)", freeKey2);
            log.info("  Configs: 2 (Fixed Window algorithm)");
            log.info("");
            log.info("PRO TIER - Acme Inc:");
            log.info("  API Key 1: {}", proKey1);
            log.info("  API Key 2: {} (expires in 1 year)", proKey2);
            log.info("  Configs: 3 (Token Bucket algorithm)");
            log.info("");
            log.info("ENTERPRISE TIER - BigTech Ltd:");
            log.info("  API Key 1: {}", enterpriseKey1);
            log.info("  API Key 2: {}", enterpriseKey2);
            log.info("  API Key 3: {} (expires in 2 years)", enterpriseKey3);
            log.info("  Configs: 2 (Sliding Window algorithm)");
            log.info("");
            log.info("=".repeat(60));
            log.info("Ready to test! Use the API keys above with /api/v1/ratelimit/check");
            log.info("=".repeat(60));
        };
    }

    /**
     * Create a tenant.
     */
    private Tenant createTenant(TenantRepository repository, String name, Tenant.TenantTier tier) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setTier(tier);
        Tenant saved = repository.save(tenant);
        log.info("Created tenant: {} ({})", name, tier);
        return saved;
    }

    /**
     * Create an API key.
     *
     * @return API key string (for logging)
     */
    private String createApiKey(ApiKeyRepository repository, Tenant tenant, LocalDateTime expiresAt) {
        String apiKeyString = ApiKeyGenerator.generate();

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setApiKey(apiKeyString);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setIsActive(true);

        repository.save(apiKey);
        log.info("Created API key: {} for tenant: {}",
                ApiKeyGenerator.mask(apiKeyString), tenant.getName());

        return apiKeyString;
    }

    /**
     * Create a rate limit configuration.
     */
    private void createConfig(
            RateLimitConfigRepository repository,
            Tenant tenant,
            String endpointPattern,
            String httpMethod,
            int maxRequests,
            int windowSeconds,
            RateLimitConfig.Algorithm algorithm,
            Double refillRate) {

        RateLimitConfig config = new RateLimitConfig();
        config.setTenant(tenant);
        config.setEndpointPattern(endpointPattern);
        config.setHttpMethod(httpMethod);
        config.setMaxRequests(maxRequests);
        config.setWindowSeconds(windowSeconds);
        config.setAlgorithm(algorithm);
        config.setRefillRate(refillRate);
        config.setIsActive(true);

        repository.save(config);
        log.info("Created config: {} {} - {} req/{} sec ({}) for tenant: {}",
                httpMethod, endpointPattern, maxRequests, windowSeconds, algorithm, tenant.getName());
    }
}
