package com.portfolio.hcm.time;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    List<TimeEntry> findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(UUID timesheetId);

    List<TimeEntry> findByTimesheetIdInAndDeletedFalseOrderByClockInAtAsc(List<UUID> timesheetIds);

    List<TimeEntry> findByTenantIdAndEmployeeIdAndEntryDateBetweenAndDeletedFalseOrderByClockInAtAsc(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    Optional<TimeEntry> findFirstByTenantIdAndEmployeeIdAndClockOutAtIsNullAndDeletedFalseOrderByClockInAtDesc(UUID tenantId, UUID employeeId);

    Optional<TimeEntry> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
