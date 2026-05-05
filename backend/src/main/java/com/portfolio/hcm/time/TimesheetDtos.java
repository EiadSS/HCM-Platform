package com.portfolio.hcm.time;

import com.portfolio.hcm.audit.AuditLogDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TimesheetDtos {
    private TimesheetDtos() {
    }

    public record TimesheetDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            LocalDate weekStartDate,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            TimesheetStatus status,
            boolean lockedPayPeriod,
            String managerNote,
            Instant submittedAt,
            Instant approvedAt
    ) {
        static TimesheetDto from(Timesheet timesheet) {
            return new TimesheetDto(
                    timesheet.getId(),
                    timesheet.getEmployeeId(),
                    timesheet.getEmployeeName(),
                    timesheet.getWeekStartDate(),
                    timesheet.getRegularHours(),
                    timesheet.getOvertimeHours(),
                    timesheet.getStatus(),
                    timesheet.isLockedPayPeriod(),
                    timesheet.getManagerNote(),
                    timesheet.getSubmittedAt(),
                    timesheet.getApprovedAt()
            );
        }
    }

    public record TimeEntryDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID shiftId,
            LocalDate entryDate,
            Instant clockInAt,
            Instant clockOutAt,
            String source,
            String status,
            String note,
            BigDecimal paidHours,
            List<TimeBreakDto> breaks
    ) {
        static TimeEntryDto from(TimeEntry entry, List<TimeBreakDto> breaks, BigDecimal paidHours) {
            return new TimeEntryDto(
                    entry.getId(),
                    entry.getEmployeeId(),
                    entry.getEmployeeName(),
                    entry.getShiftId(),
                    entry.getEntryDate(),
                    entry.getClockInAt(),
                    entry.getClockOutAt(),
                    entry.getSource().name(),
                    entry.getStatus().name(),
                    entry.getNote(),
                    paidHours,
                    breaks
            );
        }
    }

    public record TimeBreakDto(
            UUID id,
            Instant breakStartAt,
            Instant breakEndAt,
            Integer durationMinutes,
            String source,
            String note
    ) {
        static TimeBreakDto from(TimeBreak timeBreak) {
            return new TimeBreakDto(
                    timeBreak.getId(),
                    timeBreak.getBreakStartAt(),
                    timeBreak.getBreakEndAt(),
                    timeBreak.getDurationMinutes(),
                    timeBreak.getSource().name(),
                    timeBreak.getNote()
            );
        }
    }

    public record ValidationIssueDto(
            String type,
            String severity,
            String message
    ) {
        static ValidationIssueDto from(TimesheetValidationService.ValidationIssue issue) {
            return new ValidationIssueDto(issue.type(), issue.severity(), issue.message());
        }
    }

    public record TimesheetChangeRequestDto(
            UUID id,
            UUID timesheetId,
            String requesterEmail,
            String reason,
            String status,
            String decisionNote,
            Instant decidedAt,
            Instant createdAt
    ) {
        static TimesheetChangeRequestDto from(TimesheetChangeRequest request) {
            return new TimesheetChangeRequestDto(
                    request.getId(),
                    request.getTimesheetId(),
                    request.getRequesterEmail(),
                    request.getReason(),
                    request.getStatus().name(),
                    request.getDecisionNote(),
                    request.getDecidedAt(),
                    request.getCreatedAt()
            );
        }
    }

    public record TimesheetDetailDto(
            TimesheetDto timesheet,
            List<TimeEntryDto> entries,
            List<TimesheetChangeRequestDto> changeRequests,
            List<ValidationIssueDto> validationIssues,
            List<AuditLogDto> history
    ) {
    }

    public record TimeStatusDto(
            TimesheetDto currentTimesheet,
            TimeEntryDto activeEntry,
            TimeBreakDto activeBreak,
            List<ValidationIssueDto> validationIssues
    ) {
    }

    public record ClockRequest(
            Instant occurredAt,
            String note
    ) {
    }

    public record BreakRequest(
            Instant occurredAt,
            String note
    ) {
    }

    public record ManualTimeEntryRequest(
            @NotNull Instant clockInAt,
            Instant clockOutAt,
            Instant breakStartAt,
            Instant breakEndAt,
            String note
    ) {
    }

    public record ChangeRequestRequest(
            @NotBlank String reason
    ) {
    }

    public record DecisionRequest(
            String note
    ) {
    }

    public record ApprovalRequest(String note) {
    }
}
