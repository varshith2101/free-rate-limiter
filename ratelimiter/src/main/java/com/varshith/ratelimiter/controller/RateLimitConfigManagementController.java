package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.CreateConfigRequest;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.service.RateLimitConfigManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manage")
@CrossOrigin(origins = "*")
public class RateLimitConfigManagementController {

    private final RateLimitConfigManagementService configService;

    public RateLimitConfigManagementController(RateLimitConfigManagementService configService) {
        this.configService = configService;
    }

    @PostMapping("/tenants/{tenantId}/endpoints/{endpointId}/configs")
    public ResponseEntity<RateLimitConfig> createConfig(
            @PathVariable UUID tenantId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateConfigRequest request) {
        request.setTenantId(tenantId);
        request.setEndpointId(endpointId);
        RateLimitConfig config = configService.createConfig(tenantId, endpointId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    @GetMapping("/tenants/{tenantId}/endpoints/{endpointId}/configs")
    public ResponseEntity<List<RateLimitConfig>> listConfigs(
            @PathVariable UUID tenantId,
            @PathVariable UUID endpointId) {
        return ResponseEntity.ok(configService.listConfigs(tenantId, endpointId));
    }

    @DeleteMapping("/tenants/{tenantId}/configs/{configId}")
    public ResponseEntity<Map<String, String>> deleteConfig(
            @PathVariable UUID tenantId,
            @PathVariable UUID configId) {
        configService.deleteConfig(tenantId, configId);
        return ResponseEntity.ok(Map.of("message", "Configuration deleted"));
    }
}
