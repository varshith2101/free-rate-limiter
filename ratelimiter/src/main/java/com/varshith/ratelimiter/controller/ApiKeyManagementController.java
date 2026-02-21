package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.ApiKeyCreatedResponse;
import com.varshith.ratelimiter.dto.ApiKeySummaryResponse;
import com.varshith.ratelimiter.dto.CreateTenantApiKeyRequest;
import com.varshith.ratelimiter.service.ApiKeyManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-facing API key management endpoints.
 */
@RestController
@RequestMapping("/api/v1/manage")
@CrossOrigin(origins = "*")
public class ApiKeyManagementController {

    private final ApiKeyManagementService apiKeyManagementService;

    public ApiKeyManagementController(ApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @PostMapping("/tenants/{tenantId}/apikeys")
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateTenantApiKeyRequest request) {
        ApiKeyCreatedResponse response = apiKeyManagementService.createApiKey(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tenants/{tenantId}/apikeys")
    public ResponseEntity<List<ApiKeySummaryResponse>> getApiKeys(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(apiKeyManagementService.listApiKeys(tenantId));
    }

    @DeleteMapping("/tenants/{tenantId}/apikeys/{apiKeyId}")
    public ResponseEntity<Void> deleteApiKey(
            @PathVariable UUID tenantId,
            @PathVariable UUID apiKeyId) {
        apiKeyManagementService.deleteApiKey(tenantId, apiKeyId);
        return ResponseEntity.noContent().build();
    }
}
