package com.portfolio.hcm.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPreviewRepository extends JpaRepository<PayrollPreview, UUID> {
    List<PayrollPreview> findByTenantIdAndDeletedFalseOrderByPeriodStartDesc(UUID tenantId);

    Optional<PayrollPreview> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
