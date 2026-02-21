package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.*;
import com.varshith.ratelimiter.service.AnalyticsService;
import com.varshith.ratelimiter.service.EndpointManagementService;
import com.varshith.ratelimiter.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for management UI operations.
 *
 * Provides endpoints for:
 * - Endpoint management (CRUD)
 * - Analytics and monitoring
 * - Configuration management
 */
@RestController
@RequestMapping("/api/v1/manage")
@CrossOrigin(origins = "*") // Allow dashboard to connect
public class ManagementController {

    private final EndpointManagementService endpointService;
    private final AnalyticsService analyticsService;
    private final AdminService adminService;

    public ManagementController(
            EndpointManagementService endpointService,
            AnalyticsService analyticsService,
            AdminService adminService) {
        this.endpointService = endpointService;
        this.analyticsService = analyticsService;
        this.adminService = adminService;
    }

    // ==================== Endpoint Management ====================

    /**
     * Create a new endpoint for a tenant.
     *
     * POST /api/v1/manage/tenants/{tenantId}/endpoints
     */
    @PostMapping("/tenants/{tenantId}/endpoints")
    public ResponseEntity<EndpointResponse> createEndpoint(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateEndpointRequest request) {

        EndpointResponse response = endpointService.createEndpoint(tenantId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all endpoints for a tenant.
     *
     * GET /api/v1/manage/tenants/{tenantId}/endpoints?includeStats=true
     */
    @GetMapping("/tenants/{tenantId}/endpoints")
    public ResponseEntity<List<EndpointResponse>> getEndpoints(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "false") boolean includeStats) {

        List<EndpointResponse> endpoints = endpointService.getEndpoints(tenantId, includeStats);
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Get a single endpoint by ID.
     *
     * GET /api/v1/manage/endpoints/{endpointId}
     */
    @GetMapping("/endpoints/{endpointId}")
    public ResponseEntity<EndpointResponse> getEndpoint(
            @PathVariable UUID endpointId,
            @RequestParam(defaultValue = "true") boolean includeStats) {

        EndpointResponse endpoint = endpointService.getEndpoint(endpointId, includeStats);
        return ResponseEntity.ok(endpoint);
    }

    /**
     * Update an endpoint.
     *
     * PUT /api/v1/manage/endpoints/{endpointId}
     */
    @PutMapping("/endpoints/{endpointId}")
    public ResponseEntity<EndpointResponse> updateEndpoint(
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateEndpointRequest request) {

        EndpointResponse response = endpointService.updateEndpoint(endpointId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an endpoint.
     *
     * DELETE /api/v1/manage/endpoints/{endpointId}
     */
    @DeleteMapping("/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable UUID endpointId) {
        endpointService.deleteEndpoint(endpointId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle endpoint active status.
     *
     * POST /api/v1/manage/endpoints/{endpointId}/toggle
     */
    @PostMapping("/endpoints/{endpointId}/toggle")
    public ResponseEntity<EndpointResponse> toggleEndpointStatus(@PathVariable UUID endpointId) {
        EndpointResponse response = endpointService.toggleEndpointStatus(endpointId);
        return ResponseEntity.ok(response);
    }

    // ==================== Analytics ====================

    /**
     * Get analytics for a tenant.
     *
     * GET /api/v1/manage/tenants/{tenantId}/analytics?start=...&end=...
     */
    @GetMapping("/tenants/{tenantId}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable UUID tenantId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {

        // Default to last 24 hours if not specified
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDateTime startLocal;
        LocalDateTime endLocal;

        if (start == null) {
            startLocal = LocalDateTime.now(ist).minusDays(1);
        } else {
            startLocal = start.atZoneSameInstant(ist).toLocalDateTime();
        }
        if (end == null) {
            endLocal = LocalDateTime.now(ist);
        } else {
            endLocal = end.atZoneSameInstant(ist).toLocalDateTime();
        }

        AnalyticsResponse analytics = analyticsService.getAnalytics(tenantId, startLocal, endLocal);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Get tenant summary details.
     */
    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<TenantSummaryResponse> getTenantSummary(@PathVariable UUID tenantId) {
        var tenant = adminService.getTenant(tenantId);
        TenantSummaryResponse response = TenantSummaryResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .tier(tenant.getTier())
                .build();
        return ResponseEntity.ok(response);
    }

    // ==================== Health Check ====================

    /**
     * Health check endpoint for the management API.
     *
     * GET /api/v1/manage/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
