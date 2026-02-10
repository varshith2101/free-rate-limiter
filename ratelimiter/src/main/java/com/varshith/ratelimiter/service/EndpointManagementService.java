package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.CreateEndpointRequest;
import com.varshith.ratelimiter.dto.EndpointResponse;
import com.varshith.ratelimiter.dto.RateLimitConfigResponse;
import com.varshith.ratelimiter.exception.TenantNotFoundException;
import com.varshith.ratelimiter.model.Endpoint;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.repository.EndpointRepository;
import com.varshith.ratelimiter.repository.BackendLinkRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.RateLimitLogRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing endpoints and their configurations.
 */
@Service
public class EndpointManagementService {

    private static final Logger log = LoggerFactory.getLogger(EndpointManagementService.class);

    private final EndpointRepository endpointRepository;
        private final BackendLinkRepository backendLinkRepository;
    private final RateLimitConfigRepository configRepository;
    private final RateLimitLogRepository logRepository;
    private final TenantRepository tenantRepository;
        private final BackendLinkService backendLinkService;

    public EndpointManagementService(
            EndpointRepository endpointRepository,
                        BackendLinkRepository backendLinkRepository,
            RateLimitConfigRepository configRepository,
            RateLimitLogRepository logRepository,
                        TenantRepository tenantRepository,
                        BackendLinkService backendLinkService) {
        this.endpointRepository = endpointRepository;
                this.backendLinkRepository = backendLinkRepository;
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.tenantRepository = tenantRepository;
                this.backendLinkService = backendLinkService;
    }

    /**
     * Create a new endpoint for a tenant.
     */
    @Transactional
    public EndpointResponse createEndpoint(UUID tenantId, CreateEndpointRequest request) {
        log.info("Creating endpoint for tenant: {}, path: {}", tenantId, request.getPath());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

                if (request.getBackendLinkId() == null) {
                        throw new RuntimeException("Backend link is required");
                }

        var backendLink = backendLinkService.getVerifiedBackendLinkForUser(
                tenant.getUserId(),
                request.getBackendLinkId()
        );

        Endpoint endpoint = new Endpoint();
        endpoint.setTenant(tenant);
        endpoint.setBackendLinkId(backendLink.getId());
        endpoint.setBasePath(backendLink.getBackendUrl());
        endpoint.setPath(request.getPath());
        endpoint.setHttpMethod(request.getHttpMethod());
        endpoint.setDescription(request.getDescription());
        endpoint.setIsActive(true);

        endpoint = endpointRepository.save(endpoint);

        log.info("Created endpoint: {} for tenant: {}", endpoint.getId(), tenantId);

        return toEndpointResponse(endpoint, true);
    }

    /**
     * Get all endpoints for a tenant.
     */
    @Transactional(readOnly = true)
    public List<EndpointResponse> getEndpoints(UUID tenantId, boolean includeStats) {
        log.debug("Fetching endpoints for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        List<Endpoint> endpoints = endpointRepository.findByTenant(tenant);

        return endpoints.stream()
                .map(endpoint -> toEndpointResponse(endpoint, includeStats))
                .collect(Collectors.toList());
    }

    /**
     * Get a single endpoint by ID.
     */
    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(UUID endpointId, boolean includeStats) {
        log.debug("Fetching endpoint: {}", endpointId);

        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new RuntimeException("Endpoint not found: " + endpointId));

        return toEndpointResponse(endpoint, includeStats);
    }

    /**
     * Update an endpoint.
     */
    @Transactional
    public EndpointResponse updateEndpoint(UUID endpointId, CreateEndpointRequest request) {
        log.info("Updating endpoint: {}", endpointId);

        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new RuntimeException("Endpoint not found: " + endpointId));

        var backendLink = backendLinkService.getVerifiedBackendLinkForUser(
                endpoint.getTenant().getUserId(),
                request.getBackendLinkId()
        );

        endpoint.setBackendLinkId(backendLink.getId());
        endpoint.setBasePath(backendLink.getBackendUrl());
        endpoint.setPath(request.getPath());
        endpoint.setHttpMethod(request.getHttpMethod());
        endpoint.setDescription(request.getDescription());

        endpoint = endpointRepository.save(endpoint);

        return toEndpointResponse(endpoint, false);
    }

    /**
     * Delete an endpoint.
     */
    @Transactional
    public void deleteEndpoint(UUID endpointId) {
        log.info("Deleting endpoint: {}", endpointId);

        // Also delete associated configs
        List<RateLimitConfig> configs = configRepository.findByEndpointId(endpointId);
        configRepository.deleteAll(configs);

        endpointRepository.deleteById(endpointId);
    }

    /**
     * Toggle endpoint active status.
     */
    @Transactional
    public EndpointResponse toggleEndpointStatus(UUID endpointId) {
        log.info("Toggling endpoint status: {}", endpointId);

        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new RuntimeException("Endpoint not found: " + endpointId));

        endpoint.setIsActive(!endpoint.getIsActive());
        endpoint = endpointRepository.save(endpoint);

        return toEndpointResponse(endpoint, false);
    }

    /**
     * Convert Endpoint entity to response DTO.
     */
    private EndpointResponse toEndpointResponse(Endpoint endpoint, boolean includeStats) {
                String backendLinkName = null;
                String backendLinkColor = null;

                if (endpoint.getBackendLinkId() != null) {
                        var backendLink = backendLinkRepository.findById(endpoint.getBackendLinkId()).orElse(null);
                        if (backendLink != null) {
                                backendLinkName = backendLink.getNickname();
                                backendLinkColor = backendLink.getAccentColor();
                        }
                }

        EndpointResponse response = EndpointResponse.builder()
                .id(endpoint.getId())
                .basePath(endpoint.getBasePath())
                .path(endpoint.getPath())
                .httpMethod(endpoint.getHttpMethod())
                .description(endpoint.getDescription())
                                .backendLinkId(endpoint.getBackendLinkId())
                                .backendLinkName(backendLinkName)
                                .backendLinkColor(backendLinkColor)
                .fullUrl(endpoint.getFullUrl())
                .isActive(endpoint.getIsActive())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();

        // Include rate limit configs
        List<RateLimitConfig> configs = configRepository.findByEndpointId(endpoint.getId());
        response.setRateLimitConfigs(
                configs.stream()
                        .map(this::toConfigResponse)
                        .collect(Collectors.toList())
        );

        // Include stats if requested
        if (includeStats) {
            response.setStats(getEndpointStats(endpoint.getId()));
        }

        return response;
    }

    /**
     * Get statistics for an endpoint.
     */
    private EndpointResponse.EndpointStats getEndpointStats(UUID endpointId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusDays(1);

        long totalRequests = logRepository.countByTenantIdAndTimestampBetween(
                endpointId, last24h, now
        );

        long blockedRequests = logRepository.countBlockedByTenantIdAndTimestampBetween(
                endpointId, last24h, now
        );

        double blockRate = totalRequests > 0
                ? (double) blockedRequests / totalRequests * 100
                : 0.0;

        return EndpointResponse.EndpointStats.builder()
                .totalRequests(totalRequests)
                .blockedRequests(blockedRequests)
                .blockRate(Math.round(blockRate * 100.0) / 100.0)
                .lastRequestAt(now)
                .build();
    }

    /**
     * Convert RateLimitConfig entity to response DTO.
     */
    private RateLimitConfigResponse toConfigResponse(RateLimitConfig config) {
        return RateLimitConfigResponse.builder()
                .id(config.getId())
                .endpointPattern(config.getEndpointPattern())
                .httpMethod(config.getHttpMethod())
                .maxRequests(config.getMaxRequests())
                .windowSeconds(config.getWindowSeconds())
                .algorithm(config.getAlgorithm())
                .limitBy(config.getLimitBy())
                .customHeader(config.getCustomHeader())
                .refillRate(config.getRefillRate())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
