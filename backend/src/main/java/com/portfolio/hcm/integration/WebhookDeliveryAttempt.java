package com.portfolio.hcm.integration;

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
@Table(name = "webhook_delivery_attempts")
public class WebhookDeliveryAttempt extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID webhookEventId;

    @Column(nullable = false)
    private String destinationName;

    @Column(nullable = false)
    private String destinationUrl;

    @Column(nullable = false)
    private String status;

    private Integer responseCode;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Column(nullable = false)
    private Instant attemptedAt;
}
