package com.varshith.ratelimiter.dto;

import com.varshith.ratelimiter.model.Tenant.TenantTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    /**
     * Tenant name (must be unique).
     */
    @NotBlank(message = "Tenant name is required")
    private String name;

    /**
     * Tenant tier (FREE, PRO, ENTERPRISE).
     */
    @NotNull(message = "Tenant tier is required")
    private TenantTier tier;
}
