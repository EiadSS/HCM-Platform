package com.portfolio.hcm.analytics;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent extends BaseEntity {
    @Column(nullable = false)
    private String eventType;

    private String visitorHash;

    private UUID tenantId;

    private String accountEmail;

    private String accountRole;

    private String path;

    private String referrer;

    @Column(columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false)
    private Instant occurredAt;
}
