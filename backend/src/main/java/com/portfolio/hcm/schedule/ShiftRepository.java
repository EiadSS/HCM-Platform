package com.portfolio.hcm.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    List<Shift> findByTenantIdAndDeletedFalseOrderByShiftDateAscStartTimeAsc(UUID tenantId);

    List<Shift> findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(UUID tenantId, LocalDate from, LocalDate to);

    List<Shift> findByTenantIdAndEmployeeIdAndShiftDateAndDeletedFalseOrderByStartTimeAsc(UUID tenantId, UUID employeeId, LocalDate shiftDate);

    Optional<Shift> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
