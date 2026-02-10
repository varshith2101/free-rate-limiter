package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.CreateConfigRequest;
import com.varshith.ratelimiter.exception.InvalidConfigException;
import com.varshith.ratelimiter.model.Endpoint;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.repository.EndpointRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimitConfigManagementService {

    private static final int FREE_TIER_MAX_CONFIGS = 3;
    private static final int PRO_TIER_MAX_CONFIGS = 10;
    private static final int ENTERPRISE_TIER_MAX_CONFIGS = Integer.MAX_VALUE;

    private final RateLimitConfigRepository configRepository;
    private final TenantRepository tenantRepository;
    private final EndpointRepository endpointRepository;

    public RateLimitConfigManagementService(
            RateLimitConfigRepository configRepository,
            TenantRepository tenantRepository,
            EndpointRepository endpointRepository) {
        this.configRepository = configRepository;
        this.tenantRepository = tenantRepository;
        this.endpointRepository = endpointRepository;
    }

    @Transactional
    public RateLimitConfig createConfig(UUID tenantId, UUID endpointId, CreateConfigRequest request) {
        Tenant tenant = getTenant(tenantId);
        Endpoint endpoint = getEndpointForTenant(tenantId, endpointId);

        if (!configRepository.findActiveByEndpointId(endpointId).isEmpty()) {
            throw new InvalidConfigException("Only one active configuration is allowed per endpoint");
        }

        enforceTierLimit(tenant);
        applyEndpointDefaults(request, endpoint);
        validateConfigRequest(request, tenant);

        RateLimitConfig config = new RateLimitConfig();
        config.setTenant(tenant);
        config.setEndpoint(endpoint);
        config.setEndpointPattern(request.getEndpointPattern());
        config.setHttpMethod(request.getHttpMethod());
        config.setMaxRequests(request.getMaxRequests());
        config.setWindowSeconds(request.getWindowSeconds());
        config.setAlgorithm(request.getAlgorithm());
        config.setRefillRate(request.getRefillRate());
        config.setLimitBy(request.getLimitBy());
        config.setCustomHeader(request.getCustomHeader());
        config.setIsActive(true);

        return configRepository.save(config);
    }

    @Transactional(readOnly = true)
    public List<RateLimitConfig> listConfigs(UUID tenantId, UUID endpointId) {
        getEndpointForTenant(tenantId, endpointId);
        return configRepository.findActiveByEndpointId(endpointId);
    }

    @Transactional
    public void deleteConfig(UUID tenantId, UUID configId) {
        RateLimitConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!config.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized");
        }

        config.setIsActive(false);
        configRepository.save(config);
    }

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
    }

    private Endpoint getEndpointForTenant(UUID tenantId, UUID endpointId) {
        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new RuntimeException("Endpoint not found"));

        if (!endpoint.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized");
        }

        return endpoint;
    }

    private void enforceTierLimit(Tenant tenant) {
        long currentConfigCount = configRepository.countByTenantId(tenant.getId());
        int maxConfigs = switch (tenant.getTier()) {
            case FREE -> FREE_TIER_MAX_CONFIGS;
            case PRO -> PRO_TIER_MAX_CONFIGS;
            case ENTERPRISE -> ENTERPRISE_TIER_MAX_CONFIGS;
        };

        if (currentConfigCount >= maxConfigs) {
            throw InvalidConfigException.tierLimitExceeded(
                    tenant.getTier().name(), (int) currentConfigCount, maxConfigs);
        }
    }

    private void validateConfigRequest(CreateConfigRequest request, Tenant tenant) {
        if (request.getEndpointPattern() == null || request.getEndpointPattern().isBlank()) {
            throw new InvalidConfigException("Endpoint pattern is required");
        }

        if (request.getMaxRequests() <= 0) {
            throw InvalidConfigException.invalidLimit(request.getMaxRequests());
        }

        if (request.getWindowSeconds() <= 0) {
            throw InvalidConfigException.invalidWindow(request.getWindowSeconds());
        }

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

        if (request.getAlgorithm() == RateLimitConfig.Algorithm.SLIDING_WINDOW
                && tenant.getTier() == Tenant.TenantTier.FREE) {
            throw new InvalidConfigException("Sliding Window is available on the PRO tier");
        }
    }

    private void applyEndpointDefaults(CreateConfigRequest request, Endpoint endpoint) {
        if (request.getEndpointPattern() == null || request.getEndpointPattern().isBlank()) {
            request.setEndpointPattern(endpoint.getPath());
        }

        if (request.getHttpMethod() == null || request.getHttpMethod().isBlank()) {
            request.setHttpMethod(endpoint.getHttpMethod());
        }
    }
}
