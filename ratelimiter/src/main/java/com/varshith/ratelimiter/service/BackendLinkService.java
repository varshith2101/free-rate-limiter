package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.model.BackendLink;
import com.varshith.ratelimiter.repository.BackendLinkRepository;
import com.varshith.ratelimiter.util.AuthUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BackendLinkService {
    
    private final BackendLinkRepository backendLinkRepository;
    private final AuthUtil authUtil;
    private final RestTemplate restTemplate = new RestTemplate();
    
    public BackendLinkService(BackendLinkRepository backendLinkRepository, AuthUtil authUtil) {
        this.backendLinkRepository = backendLinkRepository;
        this.authUtil = authUtil;
    }
    
    /**
     * Create a backend link with verification token
        * The token must be returned by: GET /.well-known/ratelimiter-verify?token={token}
     */
    public BackendLink createBackendLink(UUID userId, String backendUrl, String nickname, String accentColor) {
        // Validate URL format
        validateUrl(backendUrl);
        validateNickname(nickname);
        validateAccentColor(accentColor);
        
        // Check if backend link already exists
        if (backendLinkRepository.findByBackendUrlAndUserId(backendUrl, userId).isPresent()) {
            throw new RuntimeException("Backend link already exists");
        }
        
        String verificationToken = authUtil.generateVerificationToken();
        
        BackendLink backendLink = BackendLink.builder()
            .userId(userId)
            .backendUrl(backendUrl)
            .nickname(nickname)
            .accentColor(accentColor)
            .verificationToken(verificationToken)
            .isVerified(false)
            .verificationAttempts(0)
            .build();
        
        return backendLinkRepository.save(backendLink);
    }
    
    /**
     * Verify backend link ownership
     * User must expose: GET {backendUrl}/.well-known/ratelimiter-verify?token={token}
     * Response must include the token in body.
     */
    public void verifyBackendLink(UUID userId, UUID linkId) {
        BackendLink backendLink = backendLinkRepository.findById(linkId)
            .orElseThrow(() -> new RuntimeException("Backend link not found"));

        if (!backendLink.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        // Check rate limiting on verification attempts
        if (backendLink.getLastVerificationAttempt() != null) {
            long secondsAgo = java.time.temporal.ChronoUnit.SECONDS
                .between(backendLink.getLastVerificationAttempt(), LocalDateTime.now());
            if (secondsAgo < 5) {
                throw new RuntimeException("Please wait before trying again");
            }
        }
        
        backendLink.setVerificationAttempts(backendLink.getVerificationAttempts() + 1);
        backendLink.setLastVerificationAttempt(LocalDateTime.now());
        
        try {
            String token = backendLink.getVerificationToken();
            String verifyUrl = backendLink.getBackendUrl() + "/.well-known/ratelimiter-verify?token=" + token;
            String response = restTemplate.getForObject(verifyUrl, String.class);

            if (response == null || !response.contains(token)) {
                throw new RuntimeException("Verification failed: token not found in response");
            }
            
            // If successful, mark as verified
            backendLink.setIsVerified(true);
            backendLinkRepository.save(backendLink);
        } catch (Exception e) {
            backendLinkRepository.save(backendLink);
            throw new RuntimeException("Failed to verify backend. Ensure the endpoint is accessible: " 
                + backendLink.getBackendUrl() + "/.well-known/ratelimiter-verify?token=" + backendLink.getVerificationToken());
        }
    }
    
    /**
     * Get all backend links for a user
     */
    public List<BackendLink> getUserBackendLinks(UUID userId) {
        return backendLinkRepository.findByUserId(userId);
    }
    
    /**
     * Delete a backend link
     */
    public void deleteBackendLink(UUID userId, UUID linkId) {
        BackendLink backendLink = backendLinkRepository.findById(linkId)
            .orElseThrow(() -> new RuntimeException("Backend link not found"));
        
        if (!backendLink.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        backendLinkRepository.delete(backendLink);
    }

    /**
     * Ensure a user has at least one verified backend link.
     */
    public void assertHasVerifiedBackend(UUID userId) {
        if (userId == null) {
            throw new RuntimeException("Tenant is not linked to a user");
        }

        List<BackendLink> verifiedLinks = backendLinkRepository.findByUserIdAndIsVerifiedTrue(userId);
        if (verifiedLinks.isEmpty()) {
            throw new RuntimeException("Backend ownership verification required before configuring rate limits");
        }
    }

    /**
     * Ensure the base path belongs to a verified backend.
     */
    public void assertVerifiedBackendForBasePath(UUID userId, String basePath) {
        if (userId == null) {
            throw new RuntimeException("Tenant is not linked to a user");
        }

        validateUrl(basePath);
        List<BackendLink> verifiedLinks = backendLinkRepository.findByUserIdAndIsVerifiedTrue(userId);
        if (verifiedLinks.isEmpty()) {
            throw new RuntimeException("Backend ownership verification required before configuring rate limits");
        }

        boolean matches = verifiedLinks.stream()
            .anyMatch(link -> basePathMatches(link.getBackendUrl(), basePath));

        if (!matches) {
            throw new RuntimeException("Base path must match a verified backend URL");
        }
    }

    public BackendLink getVerifiedBackendLinkForUser(UUID userId, UUID backendLinkId) {
        if (userId == null) {
            throw new RuntimeException("Tenant is not linked to a user");
        }

        BackendLink backendLink = backendLinkRepository.findByIdAndUserId(backendLinkId, userId)
            .orElseThrow(() -> new RuntimeException("Backend link not found"));

        if (!Boolean.TRUE.equals(backendLink.getIsVerified())) {
            throw new RuntimeException("Backend link is not verified");
        }

        return backendLink;
    }
    
    private void validateUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new RuntimeException("URL must start with http:// or https://");
            }
        } catch (java.net.URISyntaxException e) {
            throw new RuntimeException("Invalid URL format");
        }
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new RuntimeException("Nickname is required");
        }
    }

    private void validateAccentColor(String accentColor) {
        if (accentColor == null || accentColor.isBlank()) {
            throw new RuntimeException("Accent color is required");
        }

        List<String> allowed = Arrays.asList(
            "#3B82F6",
            "#10B981",
            "#F59E0B",
            "#EF4444",
            "#8B5CF6",
            "#14B8A6"
        );

        if (!allowed.contains(accentColor)) {
            throw new RuntimeException("Accent color must be one of the supported options");
        }
    }

    private boolean basePathMatches(String backendUrl, String basePath) {
        try {
            java.net.URI backendUri = new java.net.URI(backendUrl);
            java.net.URI baseUri = new java.net.URI(basePath);

            String backendScheme = backendUri.getScheme();
            String baseScheme = baseUri.getScheme();
            if (backendScheme == null || baseScheme == null || !backendScheme.equalsIgnoreCase(baseScheme)) {
                return false;
            }

            String backendHost = backendUri.getHost();
            String baseHost = baseUri.getHost();
            if (backendHost == null || baseHost == null || !backendHost.equalsIgnoreCase(baseHost)) {
                return false;
            }

            int backendPort = getEffectivePort(backendUri);
            int basePort = getEffectivePort(baseUri);
            if (backendPort != basePort) {
                return false;
            }

            String backendPath = normalizePath(backendUri.getPath());
            String basePathNormalized = normalizePath(baseUri.getPath());

            if (backendPath.equals(basePathNormalized)) {
                return true;
            }

            return basePathNormalized.startsWith(backendPath.endsWith("/") ? backendPath : backendPath + "/");
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private int getEffectivePort(java.net.URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
