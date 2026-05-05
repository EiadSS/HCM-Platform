package com.portfolio.hcm.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId);

    Optional<ImportJob> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
