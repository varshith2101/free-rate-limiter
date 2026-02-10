package com.varshith.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for nuke test progress.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NukeTestStatusResponse {
    private UUID testId;
    private String status;
    private String endpointUrl;
    private String httpMethod;
    private int totalRequests;
    private int completedRequests;
    private int acceptedRequests;
    private int rejectedRequests;
    private int errorRequests;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<String> logs;
}
