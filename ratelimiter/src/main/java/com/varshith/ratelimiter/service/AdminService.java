package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.CreateApiKeyRequest;
import com.varshith.ratelimiter.dto.CreateConfigRequest;
import com.varshith.ratelimiter.dto.CreateTenantRequest;
import com.varshith.ratelimiter.exception.InvalidConfigException;
import com.varshith.ratelimiter.exception.TenantNotFoundException;
import com.varshith.ratelimiter.model.ApiKey;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.repository.ApiKeyRepository;
import com.varshith.ratelimiter.repository.EndpointRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.RateLimitLogRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import com.varshith.ratelimiter.util.ApiKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin service for managing tenants, API keys, and rate limit configurations.
 *
 * Handles all administrative operations:
 * - Tenant management (create, get)
 * - API key generation and rotation
 * - Rate limit config CRUD operations
 * - Tier-based limits enforcement
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    // Tier-based config limits
    private static final int FREE_TIER_MAX_CONFIGS = 3;
    private static final int PRO_TIER_MAX_CONFIGS = 10;
    private static final int ENTERPRISE_TIER_MAX_CONFIGS = Integer.MAX_VALUE; // Unlimited

    private final TenantRepository tenantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitConfigRepository configRepository;
    private final RateLimitLogRepository logRepository;
    private final BackendLinkService backendLinkService;
    private final EndpointRepository endpointRepository;

    public AdminService(
            TenantRepository tenantRepository,
            ApiKeyRepository apiKeyRepository,
            RateLimitConfigRepository configRepository,
            RateLimitLogRepository logRepository,
            BackendLinkService backendLinkService,
            EndpointRepository endpointRepository) {
        this.tenantRepository = tenantRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.backendLinkService = backendLinkService;
        this.endpointRepository = endpointRepository;
    }

    /**
     * Create a new tenant.
     *
     * @param request Tenant creation request
     * @return Created tenant
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        log.info("Creating tenant: {}, tier: {}", request.getName(), request.getTier());

        // Check if tenant name already exists
        if (tenantRepository.existsByName(request.getName())) {
            throw new InvalidConfigException("Tenant with name '" + request.getName() + "' already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setTier(request.getTier());

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Tenant created: {} with ID: {}", savedTenant.getName(), savedTenant.getId());

        return savedTenant;
    }

    /**
     * Get tenant by ID.
     *
     * @param tenantId Tenant ID
     * @return Tenant
     * @throws TenantNotFoundException if not found
     */
    public Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> TenantNotFoundException.forId(tenantId));
    }

    /**
     * Get all tenants.
     *
     * @return List of all tenants
     */
    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    /**
     * Generate a new API key for a tenant.
     *
     * Process:
     * 1. Validate tenant exists
     * 2. Generate secure random API key
     * 3. Ensure uniqueness (regenerate if collision)
     * 4. Save to database
     *
     * @param request API key creation request
     * @return Generated API key
     */
    @Transactional
    public ApiKey generateApiKey(CreateApiKeyRequest request) {
        log.info("Generating API key for tenant: {}", request.getTenantId());

        // Validate tenant exists
        Tenant tenant = getTenant(request.getTenantId());

        // Generate unique API key
        String apiKeyString = generateUniqueApiKey();

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setApiKey(apiKeyString);
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKey.setName(request.getName());
        apiKey.setIsActive(true);

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);
        log.info("API key generated: {} for tenant: {}",
                ApiKeyGenerator.mask(apiKeyString), tenant.getName());

        return savedApiKey;
    }

    /**
     * Rotate (regenerate) an existing API key.
     *
     * Creates new key and marks old one as inactive.
     * Allows graceful migration period.
     *
     * @param apiKeyId ID of API key to rotate
     * @return New API key
     */
    @Transactional
    public ApiKey rotateApiKey(UUID apiKeyId) {
        log.info("Rotating API key: {}", apiKeyId);

        // Get existing API key
        ApiKey oldApiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));

        // Mark old key as inactive
        oldApiKey.setIsActive(false);
        apiKeyRepository.save(oldApiKey);

        // Generate new key
        String newApiKeyString = generateUniqueApiKey();

        ApiKey newApiKey = new ApiKey();
        newApiKey.setTenant(oldApiKey.getTenant());
        newApiKey.setApiKey(newApiKeyString);
        newApiKey.setExpiresAt(oldApiKey.getExpiresAt());
        newApiKey.setName(oldApiKey.getName());
        newApiKey.setIsActive(true);

        ApiKey savedNewApiKey = apiKeyRepository.save(newApiKey);
        log.info("API key rotated. Old: {}, New: {}",
                ApiKeyGenerator.mask(oldApiKey.getApiKey()),
                ApiKeyGenerator.mask(newApiKeyString));

        return savedNewApiKey;
    }

    /**
     * Get all API keys for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of API keys
     */
    public List<ApiKey> getApiKeys(UUID tenantId) {
        return apiKeyRepository.findByTenantId(tenantId);
    }

    /**
     * Create a new rate limit configuration.
     *
     * Enforces tier-based limits:
     * - FREE: max 3 configs
     * - PRO: max 10 configs
     * - ENTERPRISE: unlimited
     *
     * @param request Config creation request
     * @return Created configuration
     * @throws InvalidConfigException if tier limit exceeded
     */
    @Transactional
    public RateLimitConfig createRateLimitConfig(CreateConfigRequest request) {
        log.info("Creating rate limit config for tenant: {}, endpoint: {}",
                request.getTenantId(), request.getEndpointPattern());

        if (request.getTenantId() == null) {
            throw new InvalidConfigException("Tenant ID is required");
        }

        // Validate tenant exists
        Tenant tenant = getTenant(request.getTenantId());

        backendLinkService.assertHasVerifiedBackend(tenant.getUserId());

        // Check tier-based limits
        long currentConfigCount = configRepository.countByTenantId(tenant.getId());
        int maxConfigs = getMaxConfigsForTier(tenant.getTier());

        if (currentConfigCount >= maxConfigs) {
            throw InvalidConfigException.tierLimitExceeded(
                    tenant.getTier().name(), (int) currentConfigCount, maxConfigs);
        }

        // Validate configuration values
        validateConfigRequest(request);

        // Create config
        RateLimitConfig config = new RateLimitConfig();
        config.setTenant(tenant);
        if (request.getEndpointId() != null) {
            var endpoint = endpointRepository.findById(request.getEndpointId())
                .orElseThrow(() -> new RuntimeException("Endpoint not found: " + request.getEndpointId()));
            config.setEndpoint(endpoint);
        }
        config.setEndpointPattern(request.getEndpointPattern());
        config.setHttpMethod(request.getHttpMethod());
        config.setMaxRequests(request.getMaxRequests());
        config.setWindowSeconds(request.getWindowSeconds());
        config.setAlgorithm(request.getAlgorithm());
        config.setRefillRate(request.getRefillRate());
        config.setLimitBy(request.getLimitBy());
        config.setCustomHeader(request.getCustomHeader());
        config.setIsActive(true);

        RateLimitConfig savedConfig = configRepository.save(config);
        log.info("Rate limit config created with ID: {}", savedConfig.getId());

        return savedConfig;
    }

    /**
     * Get all configurations for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of configurations
     */
    public List<RateLimitConfig> getConfigsForTenant(UUID tenantId) {
        return configRepository.findAllByTenantId(tenantId);
    }

    /**
     * Get configuration by ID.
     *
     * @param configId Config ID
     * @return Configuration
     */
    public RateLimitConfig getConfig(UUID configId) {
        return configRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configId));
    }

    /**
     * Delete (deactivate) a rate limit configuration.
     *
     * Soft delete: marks as inactive instead of deleting.
     *
     * @param configId Config ID to delete
     */
    @Transactional
    public void deleteConfig(UUID configId) {
        log.info("Deleting rate limit config: {}", configId);

        RateLimitConfig config = getConfig(configId);
        logRepository.deleteByConfigId(configId);
        configRepository.delete(config);

        log.info("Rate limit config deleted: {}", configId);
    }

    /**
     * Generate unique API key (retry if collision).
     *
     * @return Unique API key string
     */
    private String generateUniqueApiKey() {
        String apiKey;
        int maxAttempts = 10;
        int attempts = 0;

        do {
            apiKey = ApiKeyGenerator.generate();
            attempts++;

            if (attempts > maxAttempts) {
                throw new RuntimeException("Failed to generate unique API key after " + maxAttempts + " attempts");
            }
        } while (apiKeyRepository.existsByApiKey(apiKey));

        return apiKey;
    }

    /**
     * Get max configs allowed for a tier.
     *
     * @param tier Tenant tier
     * @return Max number of configs
     */
    private int getMaxConfigsForTier(Tenant.TenantTier tier) {
        return switch (tier) {
            case FREE -> FREE_TIER_MAX_CONFIGS;
            case PRO -> PRO_TIER_MAX_CONFIGS;
            case ENTERPRISE -> ENTERPRISE_TIER_MAX_CONFIGS;
        };
    }

    /**
     * Validate config request values.
     *
     * @param request Request to validate
     * @throws InvalidConfigException if invalid
     */
    private void validateConfigRequest(CreateConfigRequest request) {
        if (request.getEndpointPattern() == null || request.getEndpointPattern().isBlank()) {
            throw new InvalidConfigException("Endpoint pattern is required");
        }

        if (request.getMaxRequests() <= 0) {
            throw InvalidConfigException.invalidLimit(request.getMaxRequests());
        }

        if (request.getWindowSeconds() <= 0) {
            throw InvalidConfigException.invalidWindow(request.getWindowSeconds());
        }

        // Validate refill rate for token bucket
        if (request.getAlgorithm() == RateLimitConfig.Algorithm.TOKEN_BUCKET) {
            if (request.getRefillRate() != null && request.getRefillRate() <= 0) {
                throw new InvalidConfigException("Refill rate must be positive");
            }
        }

        if (request.getLimitBy() == RateLimitConfig.LimitBy.CUSTOM_HEADER) {
            if (request.getCustomHeader() == null || request.getCustomHeader().isBlank()) {
                throw new InvalidConfigException("Custom header is required when limitBy is CUSTOM_HEADER");
            }
        }
    }
}
