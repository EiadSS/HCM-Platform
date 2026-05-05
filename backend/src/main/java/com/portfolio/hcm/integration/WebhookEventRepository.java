package com.portfolio.hcm.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    List<WebhookEvent> findByTenantIdAndDeletedFalseOrderByGeneratedAtDesc(UUID tenantId);

    Optional<WebhookEvent> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
