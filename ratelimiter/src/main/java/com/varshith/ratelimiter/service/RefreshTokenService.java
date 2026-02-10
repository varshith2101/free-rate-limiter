package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.model.RefreshToken;
import com.varshith.ratelimiter.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public RefreshToken create(UUID userId, String refreshToken) {
        RefreshToken token = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hash(refreshToken))
            .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
            .revoked(false)
            .build();
        return refreshTokenRepository.save(token);
    }

    public boolean isValid(String refreshToken) {
        return refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .filter(t -> !t.isRevoked())
            .filter(t -> LocalDateTime.now().isBefore(t.getExpiresAt()))
            .isPresent();
    }

    public UUID getUserId(String refreshToken) {
        return refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .map(RefreshToken::getUserId)
            .orElseThrow(() -> new RuntimeException("Invalid refresh token"));
    }

    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
            });
    }

    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.findAll().stream()
            .filter(t -> t.getUserId().equals(userId))
            .forEach(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
            });
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash refresh token");
        }
    }
}
