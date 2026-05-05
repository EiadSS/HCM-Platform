package com.portfolio.hcm.time;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {
    List<Timesheet> findByTenantIdAndDeletedFalseOrderByWeekStartDateDesc(UUID tenantId);

    List<Timesheet> findByTenantIdAndStatusAndDeletedFalseOrderByWeekStartDateDesc(UUID tenantId, TimesheetStatus status);

    List<Timesheet> findByTenantIdAndStatusInAndDeletedFalseOrderByWeekStartDateDesc(UUID tenantId, List<TimesheetStatus> statuses);

    List<Timesheet> findByTenantIdAndWeekStartDateBetweenAndStatusInAndDeletedFalseOrderByWeekStartDateAscEmployeeNameAsc(UUID tenantId, LocalDate from, LocalDate to, List<TimesheetStatus> statuses);

    Optional<Timesheet> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<Timesheet> findByTenantIdAndEmployeeIdAndWeekStartDateAndDeletedFalse(UUID tenantId, UUID employeeId, LocalDate weekStartDate);

    List<Timesheet> findByTenantIdAndEmployeeIdAndDeletedFalseOrderByWeekStartDateDesc(UUID tenantId, UUID employeeId);

    long deleteByTenantId(UUID tenantId);
}
