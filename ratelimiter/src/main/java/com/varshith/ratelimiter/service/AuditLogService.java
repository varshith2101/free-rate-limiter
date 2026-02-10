package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.model.AuditLog;
import com.varshith.ratelimiter.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UUID userId, UUID tenantId, String action, String status, String message,
                    String resourceType, String resourceId, String metadata) {
        AuditLog log = AuditLog.builder()
            .userId(userId)
            .tenantId(tenantId)
            .action(action)
            .status(status)
            .message(message)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .metadata(metadata)
            .build();
        auditLogRepository.save(log);
    }
}
