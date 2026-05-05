package com.portfolio.hcm.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        Instant timestamp,
        String actorEmail,
        String actionType,
        String entityType,
        UUID entityId,
        String previousValue,
        String newValue,
        String metadata
) {
    public static AuditLogDto from(AuditLog auditLog) {
        return new AuditLogDto(
                auditLog.getId(),
                auditLog.getCreatedAt(),
                auditLog.getActorEmail(),
                auditLog.getActionType(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getPreviousValue(),
                auditLog.getNewValue(),
                auditLog.getMetadata()
        );
    }
}
