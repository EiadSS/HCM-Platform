package com.portfolio.hcm.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaveAccrualRuleRepository extends JpaRepository<LeaveAccrualRule, UUID> {
    List<LeaveAccrualRule> findByTenantIdAndActiveTrueAndDeletedFalseOrderByEmploymentTypeAscLeaveTypeAsc(UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
