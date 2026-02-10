package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.NukeTestRequest;
import com.varshith.ratelimiter.dto.NukeTestStatusResponse;
import com.varshith.ratelimiter.service.NukeTestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manage")
@CrossOrigin(origins = "*")
public class NukeTestController {

    private final NukeTestService nukeTestService;

    public NukeTestController(NukeTestService nukeTestService) {
        this.nukeTestService = nukeTestService;
    }

    @PostMapping("/tenants/{tenantId}/endpoints/{endpointId}/nuke-tests")
    public ResponseEntity<Map<String, String>> startTest(
            @PathVariable UUID tenantId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody NukeTestRequest request) {
        UUID testId = nukeTestService.startTest(tenantId, endpointId, request);
        return ResponseEntity.ok(Map.of("testId", testId.toString()));
    }

    @GetMapping("/nuke-tests/{testId}")
    public ResponseEntity<NukeTestStatusResponse> getStatus(@PathVariable UUID testId) {
        return ResponseEntity.ok(nukeTestService.getStatus(testId));
    }
}
