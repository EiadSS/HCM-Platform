package com.portfolio.hcm.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    List<LeaveBalance> findByTenantIdAndDeletedFalseOrderByEmployeeNameAscLeaveTypeAsc(UUID tenantId);

    List<LeaveBalance> findByTenantIdAndEmployeeIdAndDeletedFalseOrderByLeaveTypeAsc(UUID tenantId, UUID employeeId);

    Optional<LeaveBalance> findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(UUID tenantId, UUID employeeId, String leaveType);

    long deleteByTenantId(UUID tenantId);
}
