package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ApiKey entity operations.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find API key by key string.
     *
     * @param apiKey The API key string
     * @return Optional containing API key if found
     */
    Optional<ApiKey> findByApiKey(String apiKey);

    /**
     * Find active API key by key string.
     * Checks both isActive flag and expiration date.
     *
     * @param apiKey The API key string
     * @param now Current timestamp for expiration check
     * @return Optional containing active API key if found
     */
    @Query("SELECT a FROM ApiKey a WHERE a.apiKey = :apiKey AND a.isActive = true " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    Optional<ApiKey> findActiveApiKey(String apiKey, LocalDateTime now);

    /**
     * Find all API keys for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of API keys
     */
    @Query("SELECT a FROM ApiKey a WHERE a.tenant.id = :tenantId")
    List<ApiKey> findByTenantId(UUID tenantId);

    /**
     * Find all active API keys for a tenant.
     *
     * @param tenantId Tenant ID
     * @param now Current timestamp for expiration check
     * @return List of active API keys
     */
    @Query("SELECT a FROM ApiKey a WHERE a.tenant.id = :tenantId AND a.isActive = true " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<ApiKey> findActiveByTenantId(UUID tenantId, LocalDateTime now);

    /**
     * Check if API key exists.
     *
     * @param apiKey The API key string
     * @return true if exists
     */
    boolean existsByApiKey(String apiKey);
}
