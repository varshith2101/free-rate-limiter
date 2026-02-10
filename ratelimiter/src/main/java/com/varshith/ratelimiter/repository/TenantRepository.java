package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Tenant entity operations.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find tenant by name.
     *
     * @param name Tenant name
     * @return Optional containing tenant if found
     */
    Optional<Tenant> findByName(String name);

    /**
     * Check if tenant exists by name.
     *
     * @param name Tenant name
     * @return true if exists
     */
    boolean existsByName(String name);
}
