package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RateLimitConfig entity operations.
 */
@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, UUID> {

    /**
     * Find all active configs for a tenant.
     * Ordered by pattern specificity (most specific first).
     *
     * @param tenantId Tenant ID
     * @return List of active configs, ordered by specificity
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.tenant.id = :tenantId " +
           "AND c.isActive = true " +
           "ORDER BY LENGTH(c.endpointPattern) DESC, c.endpointPattern")
    List<RateLimitConfig> findActiveByTenantId(UUID tenantId);

    /**
     * Find config for specific endpoint and method.
     * Returns exact match if exists.
     *
     * @param tenantId Tenant ID
     * @param endpoint Endpoint path
     * @param method HTTP method
     * @return Optional containing matching config
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.tenant.id = :tenantId " +
           "AND c.endpointPattern = :endpoint " +
           "AND (c.httpMethod = :method OR c.httpMethod = '*') " +
           "AND c.isActive = true " +
           "ORDER BY c.httpMethod DESC")
    Optional<RateLimitConfig> findExactMatch(UUID tenantId, String endpoint, String method);

    /**
     * Find all configs matching endpoint pattern for a tenant.
     * Supports wildcard matching.
     *
     * @param tenantId Tenant ID
     * @param endpoint Endpoint path
     * @param method HTTP method
     * @return List of matching configs, ordered by specificity
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.tenant.id = :tenantId " +
           "AND (c.endpointPattern = :endpoint " +
           "     OR c.endpointPattern = '*' " +
           "     OR (:endpoint LIKE CONCAT(SUBSTRING(c.endpointPattern, 1, LENGTH(c.endpointPattern) - 1), '%') " +
           "         AND c.endpointPattern LIKE '%*')) " +
           "AND (c.httpMethod = :method OR c.httpMethod = '*') " +
           "AND c.isActive = true " +
           "ORDER BY LENGTH(c.endpointPattern) DESC, c.httpMethod DESC")
    List<RateLimitConfig> findMatchingConfigs(UUID tenantId, String endpoint, String method);

    /**
     * Count configs for a tenant.
     * Used to enforce tier-based limits.
     *
     * @param tenantId Tenant ID
     * @return Number of configs
     */
    @Query("SELECT COUNT(c) FROM RateLimitConfig c WHERE c.tenant.id = :tenantId AND c.isActive = true")
    long countByTenantId(UUID tenantId);

    /**
     * Find all configs for a tenant (including inactive).
     *
     * @param tenantId Tenant ID
     * @return List of all configs
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.tenant.id = :tenantId")
    List<RateLimitConfig> findAllByTenantId(UUID tenantId);

    /**
     * Find all configs for a specific endpoint.
     *
     * @param endpointId Endpoint ID
     * @return List of configs for the endpoint
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.endpoint.id = :endpointId")
    List<RateLimitConfig> findByEndpointId(UUID endpointId);

       /**
        * Find active configs for a specific endpoint.
        *
        * @param endpointId Endpoint ID
        * @return List of active configs
        */
       @Query("SELECT c FROM RateLimitConfig c WHERE c.endpoint.id = :endpointId AND c.isActive = true")
       List<RateLimitConfig> findActiveByEndpointId(UUID endpointId);
}
