package com.varshith.ratelimiter.dto;

import com.varshith.ratelimiter.model.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for tenant summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryResponse {
    private UUID id;
    private String name;
    private Tenant.TenantTier tier;
}
