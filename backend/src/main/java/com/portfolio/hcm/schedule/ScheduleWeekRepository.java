package com.portfolio.hcm.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleWeekRepository extends JpaRepository<ScheduleWeek, UUID> {
    Optional<ScheduleWeek> findByTenantIdAndWeekStartDateAndDeletedFalse(UUID tenantId, LocalDate weekStartDate);

    List<ScheduleWeek> findByTenantIdAndWeekStartDateBetweenAndDeletedFalseOrderByWeekStartDateAsc(UUID tenantId, LocalDate from, LocalDate to);

    long deleteByTenantId(UUID tenantId);
}
