package com.varshith.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User-defined endpoint entity.
 *
 * Represents an endpoint that a tenant wants to rate limit.
 * Each endpoint can have one or more rate limit configurations.
 */
@Entity
@Table(name = "endpoints", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_base_path", columnList = "base_path, path")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "backend_link_id", nullable = false)
    private UUID backendLinkId;

    /**
     * Base path for the API.
     * Example: "https://api.myapp.com" or "https://myapp.com/api/v1"
     */
    @Column(name = "base_path", nullable = false)
    private String basePath;

    /**
     * Endpoint path (relative to base path).
     * Example: "/users", "/orders", "/payments"
     * Supports wildcards: "/users/*"
     */
    @Column(nullable = false)
    private String path;

    /**
     * HTTP method (GET, POST, PUT, DELETE, PATCH, etc.)
     * Use "*" for all methods
     */
    @Column(name = "http_method", nullable = false)
    private String httpMethod = "*";

    /**
     * User-friendly description of what this endpoint does.
     * Example: "Create new user account"
     */
    @Column(length = 500)
    private String description;

    /**
     * Whether this endpoint is actively being monitored.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Get the full URL for this endpoint.
     */
    public String getFullUrl() {
        String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        return cleanBase + cleanPath;
    }
}
