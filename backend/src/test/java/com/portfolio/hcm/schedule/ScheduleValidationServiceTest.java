package com.portfolio.hcm.schedule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.schedule.ScheduleValidationService.SchedulePolicy;
import static com.portfolio.hcm.schedule.ScheduleValidationService.ShiftDraft;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleValidationServiceTest {
    private final ScheduleValidationService service = new ScheduleValidationService();

    @Test
    void flagsOverlapsRestAndWeeklyCapViolations() {
        var employeeId = UUID.randomUUID();
        var date = LocalDate.of(2026, 5, 4);

        var violations = service.validate(List.of(
                new ShiftDraft(employeeId, "Jordan Kim", date, LocalTime.of(9, 0), LocalTime.of(17, 0), new BigDecimal("12")),
                new ShiftDraft(employeeId, "Jordan Kim", date, LocalTime.of(16, 0), LocalTime.of(22, 0), new BigDecimal("12")),
                new ShiftDraft(employeeId, "Jordan Kim", date.plusDays(1), LocalTime.of(6, 0), LocalTime.of(12, 0), new BigDecimal("12"))
        ), new SchedulePolicy(10, new BigDecimal("40")));

        assertThat(violations).extracting("type")
                .contains("OVERLAP", "MINIMUM_REST", "WEEKLY_CAP");
    }

    @Test
    void flagsPhaseTwoSchedulingRules() {
        var employeeId = UUID.randomUUID();
        var weekStart = LocalDate.of(2026, 5, 4);

        var violations = service.validate(weekStart, List.of(
                new ShiftDraft(employeeId, "Jordan Kim", weekStart.minusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0), new BigDecimal("40"), UUID.randomUUID(), UUID.randomUUID(), true, true, true, false, false),
                new ShiftDraft(employeeId, "Jordan Kim", weekStart.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 0), new BigDecimal("40"), UUID.randomUUID(), UUID.randomUUID(), true, true, true, false, false),
                new ShiftDraft(UUID.randomUUID(), "Missing Person", weekStart.plusDays(2), LocalTime.of(9, 0), LocalTime.of(17, 0), new BigDecimal("40"), UUID.randomUUID(), UUID.randomUUID(), false, true, true, false, false),
                new ShiftDraft(employeeId, "Jordan Kim", weekStart.plusDays(3), LocalTime.of(9, 0), LocalTime.of(17, 0), new BigDecimal("40"), UUID.randomUUID(), UUID.randomUUID(), true, false, false, true, false),
                new ShiftDraft(null, "Open Shift", weekStart.plusDays(4), LocalTime.of(16, 0), LocalTime.of(22, 0), new BigDecimal("40"), UUID.randomUUID(), UUID.randomUUID(), true, true, true, false, true)
        ), new SchedulePolicy(10, new BigDecimal("40")));

        assertThat(violations).extracting("type")
                .contains("OUTSIDE_WEEK", "INVALID_TIME", "UNKNOWN_EMPLOYEE", "UNKNOWN_DEPARTMENT", "UNKNOWN_LOCATION", "APPROVED_LEAVE", "OPEN_SHIFT");
    }
}
