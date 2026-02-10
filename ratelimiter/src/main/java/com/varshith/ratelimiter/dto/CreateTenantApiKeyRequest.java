package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for tenants to create an API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantApiKeyRequest {

    @NotBlank(message = "API key name is required")
    private String name;

    /**
     * Optional expiration date/time.
     */
    private LocalDateTime expiresAt;
}
