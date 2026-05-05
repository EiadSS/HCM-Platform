package com.portfolio.hcm.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayRuleConfigRepository extends JpaRepository<PayRuleConfig, UUID> {
    List<PayRuleConfig> findByTenantIdAndDeletedFalseOrderByEffectiveStartDateDesc(UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
