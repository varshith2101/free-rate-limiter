package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.ApiKeyCreatedResponse;
import com.varshith.ratelimiter.dto.ApiKeySummaryResponse;
import com.varshith.ratelimiter.dto.CreateTenantApiKeyRequest;
import com.varshith.ratelimiter.model.ApiKey;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.repository.ApiKeyRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import com.varshith.ratelimiter.util.ApiKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiKeyManagementService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    public ApiKeyManagementService(
            ApiKeyRepository apiKeyRepository,
            TenantRepository tenantRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public ApiKeyCreatedResponse createApiKey(UUID tenantId, CreateTenantApiKeyRequest request) {
        Tenant tenant = getTenantOrThrow(tenantId);

        String apiKeyString = generateUniqueApiKey();

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setApiKey(apiKeyString);
        apiKey.setName(request.getName());
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKey.setIsActive(true);

        ApiKey saved = apiKeyRepository.save(apiKey);

        return ApiKeyCreatedResponse.builder()
                .summary(toSummary(saved))
                .apiKey(apiKeyString)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummaryResponse> listApiKeys(UUID tenantId) {
        Tenant tenant = getTenantOrThrow(tenantId);
        return apiKeyRepository.findByTenantId(tenant.getId()).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateApiKey(UUID tenantId, UUID apiKeyId) {
        Tenant tenant = getTenantOrThrow(tenantId);
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RuntimeException("API key not found"));

        if (!apiKey.getTenant().getId().equals(tenant.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        apiKey.setIsActive(false);
        apiKeyRepository.save(apiKey);
    }

    private Tenant getTenantOrThrow(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
    }

    private ApiKeySummaryResponse toSummary(ApiKey apiKey) {
        return ApiKeySummaryResponse.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .prefix(ApiKeyGenerator.mask(apiKey.getApiKey()))
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .isActive(apiKey.getIsActive())
                .build();
    }

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
}
