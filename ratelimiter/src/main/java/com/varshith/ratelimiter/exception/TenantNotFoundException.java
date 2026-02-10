package com.varshith.ratelimiter.exception;

import java.util.UUID;

/**
 * Exception thrown when a tenant is not found.
 *
 * HTTP Status: 404 Not Found
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static TenantNotFoundException forId(UUID tenantId) {
        return new TenantNotFoundException(
                String.format("Tenant not found with ID: %s", tenantId));
    }

    public static TenantNotFoundException forName(String name) {
        return new TenantNotFoundException(
                String.format("Tenant not found with name: %s", name));
    }
}
