package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEndpointRequest {

    private String basePath;

    @NotNull(message = "Backend link is required")
    private UUID backendLinkId;

    @NotBlank(message = "Path is required")
    private String path;

    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|\\*", message = "Invalid HTTP method")
    @Builder.Default
    private String httpMethod = "*";

    private String description;
}
