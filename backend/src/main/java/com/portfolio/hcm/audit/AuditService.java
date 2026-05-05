package com.portfolio.hcm.audit;

import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void record(String actionType, String entityType, UUID entityId, String previousValue, String newValue, String metadata) {
        var user = currentUserService.requireUser();
        record(user, actionType, entityType, entityId, previousValue, newValue, metadata);
    }

    @Transactional
    public void record(AuthenticatedUser user, String actionType, String entityType, UUID entityId, String previousValue, String newValue, String metadata) {
        auditLogRepository.save(AuditLog.builder()
                .tenantId(user.tenantId())
                .actorUserId(user.userId())
                .actorEmail(user.email())
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .previousValue(previousValue)
                .newValue(newValue)
                .metadata(metadata)
                .build());
    }

    @Transactional
    public void recordSystem(UUID tenantId, String actionType, String entityType, UUID entityId, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .actorEmail("system@demo-seed")
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .newValue(newValue)
                .metadata("{\"source\":\"demo-seeder\"}")
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(UUID tenantId) {
        return search(tenantId, new AuditLogQuery(null, null, null, null, null, null, 25));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> search(UUID tenantId, AuditLogQuery query) {
        var cleaned = query.normalized();
        return auditLogRepository.search(
                tenantId,
                cleaned.from(),
                cleaned.to(),
                cleaned.actorEmail(),
                cleaned.actionType(),
                cleaned.entityType(),
                cleaned.entityId(),
                PageRequest.of(0, cleaned.limit())
        );
    }

    public record AuditLogQuery(
            Instant from,
            Instant to,
            String actorEmail,
            String actionType,
            String entityType,
            UUID entityId,
            Integer limit
    ) {
        private static final int DEFAULT_LIMIT = 50;
        private static final int MAX_LIMIT = 100;

        AuditLogQuery normalized() {
            return new AuditLogQuery(
                    from,
                    to,
                    clean(actorEmail),
                    clean(actionType),
                    clean(entityType),
                    entityId,
                    Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT))
            );
        }

        private static String clean(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }
}
