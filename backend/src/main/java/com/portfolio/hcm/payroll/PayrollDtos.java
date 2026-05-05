package com.portfolio.hcm.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayrollDtos {
    private PayrollDtos() {
    }

    public record PayrollPreviewRequest(
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID locationId
    ) {
    }

    public record PayrollPreviewDto(
            UUID id,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID locationId,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal unpaidBreakHours,
            BigDecimal unpaidLeaveHours,
            BigDecimal holidayHours,
            BigDecimal holidayPremiumPay,
            BigDecimal grossPay,
            int employeeCount,
            int timesheetCount,
            String status,
            String explanation
    ) {
        static PayrollPreviewDto from(PayrollPreview preview) {
            return new PayrollPreviewDto(
                    preview.getId(),
                    preview.getPeriodStart(),
                    preview.getPeriodEnd(),
                    preview.getLocationId(),
                    preview.getRegularHours(),
                    preview.getOvertimeHours(),
                    preview.getUnpaidBreakHours(),
                    preview.getUnpaidLeaveHours(),
                    preview.getHolidayHours(),
                    preview.getHolidayPremiumPay(),
                    preview.getGrossPay(),
                    preview.getEmployeeCount(),
                    preview.getTimesheetCount(),
                    preview.getStatus(),
                    preview.getExplanation()
            );
        }
    }

    public record PayrollPreviewLineDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID locationId,
            String locationName,
            int timesheetCount,
            BigDecimal hourlyRate,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal holidayHours,
            BigDecimal unpaidBreakHours,
            BigDecimal unpaidLeaveHours,
            BigDecimal regularPay,
            BigDecimal overtimePay,
            BigDecimal holidayPremiumPay,
            BigDecimal grossPay,
            String ruleName,
            String explanation
    ) {
        static PayrollPreviewLineDto from(PayrollPreviewLine line) {
            return new PayrollPreviewLineDto(
                    line.getId(),
                    line.getEmployeeId(),
                    line.getEmployeeName(),
                    line.getLocationId(),
                    line.getLocationName(),
                    line.getTimesheetCount(),
                    line.getHourlyRate(),
                    line.getRegularHours(),
                    line.getOvertimeHours(),
                    line.getHolidayHours(),
                    line.getUnpaidBreakHours(),
                    line.getUnpaidLeaveHours(),
                    line.getRegularPay(),
                    line.getOvertimePay(),
                    line.getHolidayPremiumPay(),
                    line.getGrossPay(),
                    line.getRuleName(),
                    line.getExplanation()
            );
        }
    }

    public record PayrollPreviewDetailDto(
            PayrollPreviewDto preview,
            List<PayrollPreviewLineDto> lines
    ) {
    }
}
