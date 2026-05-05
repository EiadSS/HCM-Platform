package com.portfolio.hcm.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollHolidayRepository extends JpaRepository<PayrollHoliday, UUID> {
    List<PayrollHoliday> findByTenantIdAndHolidayDateBetweenAndDeletedFalseOrderByHolidayDateAsc(UUID tenantId, LocalDate start, LocalDate end);

    long deleteByTenantId(UUID tenantId);
}
