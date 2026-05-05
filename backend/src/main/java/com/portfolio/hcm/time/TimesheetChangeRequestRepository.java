package com.portfolio.hcm.time;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetChangeRequestRepository extends JpaRepository<TimesheetChangeRequest, UUID> {
    List<TimesheetChangeRequest> findByTimesheetIdAndDeletedFalseOrderByCreatedAtDesc(UUID timesheetId);

    List<TimesheetChangeRequest> findByTenantIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, TimesheetChangeRequestStatus status);

    Optional<TimesheetChangeRequest> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByTimesheetIdAndStatusAndDeletedFalse(UUID timesheetId, TimesheetChangeRequestStatus status);

    long deleteByTenantId(UUID tenantId);
}
