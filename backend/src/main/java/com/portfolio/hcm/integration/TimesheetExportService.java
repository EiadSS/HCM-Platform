package com.portfolio.hcm.integration;

import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.time.Timesheet;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.portfolio.hcm.integration.IntegrationDtos.TimesheetExportRowDto;

@Service
public class TimesheetExportService {
    private final TimesheetRepository timesheetRepository;
    private final CurrentUserService currentUserService;

    public TimesheetExportService(TimesheetRepository timesheetRepository, CurrentUserService currentUserService) {
        this.timesheetRepository = timesheetRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<TimesheetExportRowDto> approvedRows() {
        return timesheetRepository.findByTenantIdAndStatusAndDeletedFalseOrderByWeekStartDateDesc(currentUserService.tenantId(), TimesheetStatus.APPROVED).stream()
                .map(this::row)
                .toList();
    }

    @Transactional(readOnly = true)
    public String approvedCsv() {
        var rows = new StringBuilder("timesheetId,employeeId,employeeName,weekStartDate,regularHours,overtimeHours,status,approvedAt,lockedPayPeriod,managerNote\n");
        approvedRows().forEach(row -> rows.append("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n".formatted(
                row.timesheetId(),
                row.employeeId(),
                csv(row.employeeName()),
                row.weekStartDate(),
                row.regularHours(),
                row.overtimeHours(),
                row.status(),
                row.approvedAt() == null ? "" : row.approvedAt(),
                row.lockedPayPeriod(),
                csv(row.managerNote())
        )));
        return rows.toString();
    }

    private TimesheetExportRowDto row(Timesheet timesheet) {
        return new TimesheetExportRowDto(
                timesheet.getId(),
                timesheet.getEmployeeId(),
                timesheet.getEmployeeName(),
                timesheet.getWeekStartDate(),
                timesheet.getRegularHours(),
                timesheet.getOvertimeHours(),
                timesheet.getStatus().name(),
                timesheet.getApprovedAt(),
                timesheet.isLockedPayPeriod(),
                timesheet.getManagerNote()
        );
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
