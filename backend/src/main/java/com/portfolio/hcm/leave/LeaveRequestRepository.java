package com.portfolio.hcm.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    List<LeaveRequest> findByTenantIdAndDeletedFalseOrderByStartDateAsc(UUID tenantId);

    List<LeaveRequest> findByTenantIdAndEmployeeIdAndDeletedFalseOrderByStartDateDesc(UUID tenantId, UUID employeeId);

    List<LeaveRequest> findByTenantIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualAndDeletedFalseOrderByStartDateAsc(UUID tenantId, LocalDate from, LocalDate to);

    List<LeaveRequest> findByTenantIdAndStatusAndLeaveTypeAndEndDateGreaterThanEqualAndStartDateLessThanEqualAndDeletedFalse(UUID tenantId, String status, String leaveType, LocalDate from, LocalDate to);

    Optional<LeaveRequest> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
