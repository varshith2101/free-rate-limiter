package com.varshith.ratelimiter.controller;

import com.varshith.ratelimiter.dto.*;
import com.varshith.ratelimiter.service.AuthService;
import com.varshith.ratelimiter.service.BackendLinkService;
import com.varshith.ratelimiter.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final AuthService authService;
    private final BackendLinkService backendLinkService;
    private final JwtUtil jwtUtil;
    
    public AuthController(AuthService authService, BackendLinkService backendLinkService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.backendLinkService = backendLinkService;
        this.jwtUtil = jwtUtil;
    }
    
    /**
     * Step 1: Send OTP to email
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestBody SignUpRequest request) {
        authService.sendOtp(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP sent to " + request.getEmail());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Step 2: Verify OTP and create account
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtpAndSignUp(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Login with email and password
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Logout (revoke refresh token)
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current user
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = extractToken(authHeader);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }
        
        UUID userId = jwtUtil.extractUserId(token);
        UserDto user = authService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Register backend link for verification
     */
    @PostMapping("/backend-links")
    public ResponseEntity<?> createBackendLink(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @Valid @RequestBody CreateBackendLinkRequest request
    ) {
        String token = extractToken(authHeader);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }
        
        UUID userId = jwtUtil.extractUserId(token);
        
        var backendLink = backendLinkService.createBackendLink(
            userId,
            request.getBackendUrl(),
            request.getNickname(),
            request.getAccentColor()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", backendLink.getId());
        response.put("backendUrl", backendLink.getBackendUrl());
        response.put("nickname", backendLink.getNickname());
        response.put("accentColor", backendLink.getAccentColor());
        response.put("verificationToken", backendLink.getVerificationToken());
        response.put("verificationPath", "/.well-known/ratelimiter-verify?token=" + backendLink.getVerificationToken());
        response.put("message", "Place the token on your backend at the verification path, then click verify.");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Trigger verification check for a backend link
     */
    @PostMapping("/backend-links/{linkId}/verify")
    public ResponseEntity<Map<String, String>> verifyBackendLink(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @PathVariable UUID linkId
    ) {
        String token = extractToken(authHeader);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }

        UUID userId = jwtUtil.extractUserId(token);
        backendLinkService.verifyBackendLink(userId, linkId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Backend link verified successfully");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user's backend links
     */
    @GetMapping("/backend-links")
    public ResponseEntity<?> getUserBackendLinks(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = extractToken(authHeader);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }
        
        UUID userId = jwtUtil.extractUserId(token);
        var backendLinks = backendLinkService.getUserBackendLinks(userId);
        
        return ResponseEntity.ok(backendLinks);
    }
    
    /**
     * Delete a backend link
     */
    @DeleteMapping("/backend-links/{linkId}")
    public ResponseEntity<Map<String, String>> deleteBackendLink(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @PathVariable UUID linkId
    ) {
        String token = extractToken(authHeader);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }
        
        UUID userId = jwtUtil.extractUserId(token);
        backendLinkService.deleteBackendLink(userId, linkId);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Backend link deleted");
        return ResponseEntity.ok(response);
    }
    
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid authorization header");
        }
        return authHeader.substring(7);
    }
}
