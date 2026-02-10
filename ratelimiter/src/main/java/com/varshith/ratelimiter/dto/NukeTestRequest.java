package com.varshith.ratelimiter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for running a load test against an endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NukeTestRequest {

    @Min(value = 1, message = "Total requests must be at least 1")
    @Max(value = 2000, message = "Total requests cannot exceed 2000")
    private int totalRequests;

    @Min(value = 1, message = "Concurrency must be at least 1")
    @Max(value = 200, message = "Concurrency cannot exceed 200")
    private int concurrency;
}
