package com.portfolio.hcm.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveBalanceEventRepository extends JpaRepository<LeaveBalanceEvent, UUID> {
    List<LeaveBalanceEvent> findByTenantIdAndEmployeeIdAndDeletedFalseOrderByEventDateDesc(UUID tenantId, UUID employeeId);

    boolean existsByTenantIdAndEmployeeIdAndLeaveTypeAndEventTypeAndAccrualPeriodAndDeletedFalse(UUID tenantId, UUID employeeId, String leaveType, String eventType, LocalDate accrualPeriod);

    long deleteByTenantId(UUID tenantId);
}
