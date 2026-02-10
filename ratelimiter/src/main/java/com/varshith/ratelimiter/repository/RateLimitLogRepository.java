package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.RateLimitLog;
import com.varshith.ratelimiter.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for RateLimitLog entity operations.
 */
@Repository
public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, UUID> {

    /**
     * Find logs for a tenant within a time range.
     *
     * @param tenant Tenant entity
     * @param start Start time
     * @param end End time
     * @param pageable Pagination info
     * @return Page of logs
     */
    Page<RateLimitLog> findByTenantAndTimestampBetween(
        Tenant tenant,
        LocalDateTime start,
        LocalDateTime end,
        Pageable pageable
    );

    /**
     * Find logs for an endpoint within a time range.
     *
     * @param endpointId Endpoint ID
     * @param start Start time
     * @param end End time
     * @param pageable Pagination info
     * @return Page of logs
     */
    @Query("SELECT l FROM RateLimitLog l WHERE l.endpoint.id = :endpointId AND l.timestamp BETWEEN :start AND :end")
    Page<RateLimitLog> findByEndpointIdAndTimestampBetween(
        @Param("endpointId") UUID endpointId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );

    /**
     * Count total requests for a tenant in time range.
     *
     * @param tenantId Tenant ID
     * @param start Start time
     * @param end End time
     * @return Count of requests
     */
    @Query("SELECT COUNT(l) FROM RateLimitLog l WHERE l.tenant.id = :tenantId AND l.timestamp BETWEEN :start AND :end")
    long countByTenantIdAndTimestampBetween(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Count blocked requests for a tenant in time range.
     *
     * @param tenantId Tenant ID
     * @param start Start time
     * @param end End time
     * @return Count of blocked requests
     */
    @Query("SELECT COUNT(l) FROM RateLimitLog l WHERE l.tenant.id = :tenantId AND l.allowed = false AND l.timestamp BETWEEN :start AND :end")
    long countBlockedByTenantIdAndTimestampBetween(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Get request counts grouped by endpoint.
     *
     * @param tenantId Tenant ID
     * @param start Start time
     * @param end End time
     * @return List of [endpoint_id, count] tuples
     */
    @Query("SELECT l.endpoint.id, COUNT(l) FROM RateLimitLog l WHERE l.tenant.id = :tenantId AND l.timestamp BETWEEN :start AND :end GROUP BY l.endpoint.id")
    List<Object[]> countByEndpointGrouped(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Get request counts grouped by country.
     *
     * @param tenantId Tenant ID
     * @param start Start time
     * @param end End time
     * @return List of [country_code, count] tuples
     */
    @Query("SELECT l.countryCode, COUNT(l) FROM RateLimitLog l WHERE l.tenant.id = :tenantId AND l.timestamp BETWEEN :start AND :end AND l.countryCode IS NOT NULL GROUP BY l.countryCode")
    List<Object[]> countByCountryGrouped(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Get request counts over time (hourly buckets).
     *
     * @param tenantId Tenant ID
     * @param start Start time
     * @param end End time
     * @return List of [hour, total_count, blocked_count] tuples
     */
    @Query(value = """
        SELECT
            DATE_TRUNC('hour', timestamp) as hour,
            COUNT(*) as total_count,
            SUM(CASE WHEN allowed = false THEN 1 ELSE 0 END) as blocked_count
        FROM rate_limit_logs
        WHERE tenant_id = :tenantId
            AND timestamp BETWEEN :start AND :end
        GROUP BY DATE_TRUNC('hour', timestamp)
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> getHourlyStats(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Delete old logs (for cleanup).
     *
     * @param before Delete logs before this time
     */
    void deleteByTimestampBefore(LocalDateTime before);
}
