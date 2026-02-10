package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a backend link.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBackendLinkRequest {

    @NotBlank(message = "Backend URL is required")
    private String backendUrl;

    @NotBlank(message = "Nickname is required")
    private String nickname;

    @NotBlank(message = "Accent color is required")
    private String accentColor;
}
