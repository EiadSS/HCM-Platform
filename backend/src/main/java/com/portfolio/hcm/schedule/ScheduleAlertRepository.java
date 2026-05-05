package com.portfolio.hcm.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleAlertRepository extends JpaRepository<ScheduleAlert, UUID> {
    List<ScheduleAlert> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId);

    List<ScheduleAlert> findByTenantIdAndWeekStartDateAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, LocalDate weekStartDate);

    long deleteByTenantIdAndWeekStartDate(UUID tenantId, LocalDate weekStartDate);

    long deleteByTenantId(UUID tenantId);
}
