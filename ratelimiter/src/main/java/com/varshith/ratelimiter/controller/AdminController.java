package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.CreateApiKeyRequest;
import com.varshith.ratelimiter.dto.CreateConfigRequest;
import com.varshith.ratelimiter.dto.CreateTenantRequest;
import com.varshith.ratelimiter.model.ApiKey;
import com.varshith.ratelimiter.model.RateLimitConfig;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.service.AdminService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for administrative operations.
 *
 * Manages:
 * - Tenant creation and retrieval
 * - API key generation and rotation
 * - Rate limit configuration CRUD
 *
 * Note: In production, these endpoints should be protected with
 * admin authentication (OAuth2, JWT, etc.).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ==================== Tenant Management ====================

    /**
     * Create a new tenant.
     *
     * POST /api/v1/admin/tenants
     * Body: { "name": "Acme Corp", "tier": "PRO" }
     *
     * @param request Tenant creation request
     * @return Created tenant (201 Created)
     */
    @PostMapping("/tenants")
    public ResponseEntity<Tenant> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        log.info("Creating tenant: {}", request.getName());

        Tenant tenant = adminService.createTenant(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    /**
     * Get tenant by ID.
     *
     * GET /api/v1/admin/tenants/{id}
     *
     * @param id Tenant ID
     * @return Tenant (200 OK) or 404 if not found
     */
    @GetMapping("/tenants/{id}")
    public ResponseEntity<Tenant> getTenant(@PathVariable UUID id) {
        log.debug("Getting tenant: {}", id);

        Tenant tenant = adminService.getTenant(id);

        return ResponseEntity.ok(tenant);
    }

    /**
     * Get all tenants.
     *
     * GET /api/v1/admin/tenants
     *
     * @return List of all tenants (200 OK)
     */
    @GetMapping("/tenants")
    public ResponseEntity<List<Tenant>> getAllTenants() {
        log.debug("Getting all tenants");

        List<Tenant> tenants = adminService.getAllTenants();

        return ResponseEntity.ok(tenants);
    }

    // ==================== API Key Management ====================

    /**
     * Generate a new API key for a tenant.
     *
     * POST /api/v1/admin/apikeys
     * Body: { "tenantId": "uuid", "expiresAt": "2025-12-31T23:59:59" }
     *
     * @param request API key creation request
     * @return Generated API key (201 Created)
     */
    @PostMapping("/apikeys")
    public ResponseEntity<ApiKey> generateApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        log.info("Generating API key for tenant: {}", request.getTenantId());

        ApiKey apiKey = adminService.generateApiKey(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiKey);
    }

    /**
     * Rotate (regenerate) an existing API key.
     *
     * PUT /api/v1/admin/apikeys/{id}/rotate
     *
     * @param id API key ID to rotate
     * @return New API key (200 OK)
     */
    @PutMapping("/apikeys/{id}/rotate")
    public ResponseEntity<ApiKey> rotateApiKey(@PathVariable UUID id) {
        log.info("Rotating API key: {}", id);

        ApiKey newApiKey = adminService.rotateApiKey(id);

        return ResponseEntity.ok(newApiKey);
    }

    /**
     * Get all API keys for a tenant.
     *
     * GET /api/v1/admin/tenants/{tenantId}/apikeys
     *
     * @param tenantId Tenant ID
     * @return List of API keys (200 OK)
     */
    @GetMapping("/tenants/{tenantId}/apikeys")
    public ResponseEntity<List<ApiKey>> getApiKeys(@PathVariable UUID tenantId) {
        log.debug("Getting API keys for tenant: {}", tenantId);

        List<ApiKey> apiKeys = adminService.getApiKeys(tenantId);

        return ResponseEntity.ok(apiKeys);
    }

    // ==================== Rate Limit Config Management ====================

    /**
     * Create a new rate limit configuration.
     *
     * POST /api/v1/admin/configs
     * Body: {
     *   "tenantId": "uuid",
     *   "endpointPattern": "/api/users/*",
     *   "httpMethod": "GET",
     *   "maxRequests": 100,
     *   "windowSeconds": 60,
     *   "algorithm": "TOKEN_BUCKET"
     * }
     *
     * @param request Config creation request
     * @return Created configuration (201 Created)
     */
    @PostMapping("/configs")
    public ResponseEntity<RateLimitConfig> createConfig(@Valid @RequestBody CreateConfigRequest request) {
        log.info("Creating rate limit config for tenant: {}, endpoint: {}",
                request.getTenantId(), request.getEndpointPattern());

        RateLimitConfig config = adminService.createRateLimitConfig(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    /**
     * Get all rate limit configs for a tenant.
     *
     * GET /api/v1/admin/tenants/{tenantId}/configs
     *
     * @param tenantId Tenant ID
     * @return List of configurations (200 OK)
     */
    @GetMapping("/tenants/{tenantId}/configs")
    public ResponseEntity<List<RateLimitConfig>> getConfigsForTenant(@PathVariable UUID tenantId) {
        log.debug("Getting configs for tenant: {}", tenantId);

        List<RateLimitConfig> configs = adminService.getConfigsForTenant(tenantId);

        return ResponseEntity.ok(configs);
    }

    /**
     * Get configuration by ID.
     *
     * GET /api/v1/admin/configs/{id}
     *
     * @param id Config ID
     * @return Configuration (200 OK)
     */
    @GetMapping("/configs/{id}")
    public ResponseEntity<RateLimitConfig> getConfig(@PathVariable UUID id) {
        log.debug("Getting config: {}", id);

        RateLimitConfig config = adminService.getConfig(id);

        return ResponseEntity.ok(config);
    }

    /**
     * Delete (deactivate) a rate limit configuration.
     *
     * DELETE /api/v1/admin/configs/{id}
     *
     * @param id Config ID to delete
     * @return Success message (200 OK)
     */
    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Map<String, String>> deleteConfig(@PathVariable UUID id) {
        log.info("Deleting config: {}", id);

        adminService.deleteConfig(id);

        return ResponseEntity.ok(Map.of(
                "message", "Configuration deleted successfully",
                "configId", id.toString()
        ));
    }
}
