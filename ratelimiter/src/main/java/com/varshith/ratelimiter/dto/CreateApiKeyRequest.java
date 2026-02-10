package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for generating a new API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {

    /**
     * Tenant ID this API key belongs to.
     */
    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    /**
     * Optional: Expiration date/time.
     * If not specified, API key will not expire.
     */
    private LocalDateTime expiresAt;

    /**
     * Optional: Friendly name for this API key.
     */
    private String name;
}
