package com.portfolio.hcm.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop25ByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId);

    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, String entityType, UUID entityId);

    @Query("""
            select auditLog
            from AuditLog auditLog
            where auditLog.tenantId = :tenantId
              and auditLog.deleted = false
              and (:fromInstant is null or auditLog.createdAt >= :fromInstant)
              and (:toInstant is null or auditLog.createdAt <= :toInstant)
              and (:actorEmail is null or lower(auditLog.actorEmail) like lower(concat('%', :actorEmail, '%')))
              and (:actionType is null or auditLog.actionType = :actionType)
              and (:entityType is null or auditLog.entityType = :entityType)
              and (:entityId is null or auditLog.entityId = :entityId)
            order by auditLog.createdAt desc
            """)
    List<AuditLog> search(
            @Param("tenantId") UUID tenantId,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("actorEmail") String actorEmail,
            @Param("actionType") String actionType,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            Pageable pageable
    );

    long deleteByTenantId(UUID tenantId);
}
