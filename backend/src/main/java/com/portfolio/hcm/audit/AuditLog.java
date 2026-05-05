package com.portfolio.hcm.audit;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    private UUID actorUserId;

    @Column(nullable = false)
    private String actorEmail;

    @Column(nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String entityType;

    private UUID entityId;

    @Column(columnDefinition = "text")
    private String previousValue;

    @Column(columnDefinition = "text")
    private String newValue;

    @Column(columnDefinition = "text")
    private String metadata;
}
