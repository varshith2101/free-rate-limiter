package com.varshith.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantTier tier; // FREE, PRO, ENTERPRISE

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum TenantTier {
        FREE,       // 100 requests/min
        PRO,        // 1000 requests/min
        ENTERPRISE  // Unlimited
    }
}
