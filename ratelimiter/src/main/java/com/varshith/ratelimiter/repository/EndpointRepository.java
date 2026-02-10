package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.Endpoint;
import com.varshith.ratelimiter.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Endpoint entity operations.
 */
@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {

    /**
     * Find all endpoints for a tenant.
     *
     * @param tenant Tenant entity
     * @return List of endpoints
     */
    List<Endpoint> findByTenant(Tenant tenant);

    /**
     * Find all endpoints for a tenant (by tenant ID).
     *
     * @param tenantId Tenant ID
     * @return List of endpoints
     */
    @Query("SELECT e FROM Endpoint e WHERE e.tenant.id = :tenantId")
    List<Endpoint> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Find all active endpoints for a tenant.
     *
     * @param tenant Tenant entity
     * @return List of active endpoints
     */
    List<Endpoint> findByTenantAndIsActiveTrue(Tenant tenant);

    /**
     * Find endpoint by tenant, path, and method.
     *
     * @param tenant Tenant entity
     * @param path Endpoint path
     * @param httpMethod HTTP method
     * @return Optional containing endpoint if found
     */
    Optional<Endpoint> findByTenantAndPathAndHttpMethod(Tenant tenant, String path, String httpMethod);

    /**
     * Find endpoints matching a base path.
     *
     * @param basePath Base path to search
     * @return List of matching endpoints
     */
    List<Endpoint> findByBasePath(String basePath);

    /**
     * Check if endpoint exists for tenant.
     *
     * @param tenant Tenant entity
     * @param path Endpoint path
     * @param httpMethod HTTP method
     * @return true if exists
     */
    boolean existsByTenantAndPathAndHttpMethod(Tenant tenant, String path, String httpMethod);

    /**
     * Count endpoints for a tenant.
     *
     * @param tenant Tenant entity
     * @return Count of endpoints
     */
    long countByTenant(Tenant tenant);
}
