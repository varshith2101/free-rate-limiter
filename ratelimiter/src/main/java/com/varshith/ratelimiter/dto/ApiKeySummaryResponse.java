package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for API key summaries (does not expose raw key).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeySummaryResponse {
    private UUID id;
    private String name;
    private String prefix;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean isActive;
}
