package com.varshith.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "backend_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackendLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String backendUrl;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String accentColor;
    
    @Column(nullable = false)
    private String verificationToken;
    
    @Column(nullable = false)
    private Boolean isVerified;
    
    @Column(name = "verification_attempts")
    private Integer verificationAttempts;
    
    @Column(name = "last_verification_attempt")
    private LocalDateTime lastVerificationAttempt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
