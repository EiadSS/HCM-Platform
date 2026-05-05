package com.portfolio.hcm.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {
    List<WebhookDeliveryAttempt> findByTenantIdAndWebhookEventIdAndDeletedFalseOrderByAttemptedAtDesc(UUID tenantId, UUID webhookEventId);

    Optional<WebhookDeliveryAttempt> findTopByTenantIdAndWebhookEventIdAndDeletedFalseOrderByAttemptedAtDesc(UUID tenantId, UUID webhookEventId);

    long deleteByTenantId(UUID tenantId);
}
