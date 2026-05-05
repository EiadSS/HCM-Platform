package com.portfolio.hcm.integration;

import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.time.Timesheet;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimesheetExportServiceTest {
    @Test
    void exportsApprovedTimesheetsOnlyWithStableCsvColumns() {
        var tenantId = UUID.randomUUID();
        var repository = mock(TimesheetRepository.class);
        var currentUserService = mock(CurrentUserService.class);
        var service = new TimesheetExportService(repository, currentUserService);
        when(currentUserService.tenantId()).thenReturn(tenantId);
        when(repository.findByTenantIdAndStatusAndDeletedFalseOrderByWeekStartDateDesc(tenantId, TimesheetStatus.APPROVED))
                .thenReturn(List.of(timesheet(TimesheetStatus.APPROVED)));

        var csv = service.approvedCsv();
        var jsonRows = service.approvedRows();

        assertThat(csv).startsWith("timesheetId,employeeId,employeeName,weekStartDate,regularHours,overtimeHours,status,approvedAt,lockedPayPeriod,managerNote");
        assertThat(csv).contains("APPROVED").doesNotContain("DRAFT");
        assertThat(jsonRows).hasSize(1);
        assertThat(jsonRows.get(0).status()).isEqualTo("APPROVED");
    }

    private Timesheet timesheet(TimesheetStatus status) {
        var timesheet = Timesheet.builder()
                .tenantId(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .employeeName("Elena Garcia")
                .weekStartDate(LocalDate.of(2026, 4, 27))
                .regularHours(new BigDecimal("5.50"))
                .overtimeHours(new BigDecimal("0.00"))
                .status(status)
                .lockedPayPeriod(true)
                .managerNote("Approved")
                .build();
        timesheet.setId(UUID.randomUUID());
        return timesheet;
    }
}
