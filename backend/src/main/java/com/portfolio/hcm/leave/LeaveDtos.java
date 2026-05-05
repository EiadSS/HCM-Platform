package com.portfolio.hcm.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveDtos {
    private LeaveDtos() {
    }

    public record LeaveRequestDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID requestedByUserId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hours,
            String status,
            boolean conflict,
            int conflictCount,
            String conflictSummary,
            Instant submittedAt,
            UUID decidedByUserId,
            Instant decidedAt,
            String employeeNote,
            String managerNote,
            String decisionNote
    ) {
        static LeaveRequestDto from(LeaveRequest request) {
            return new LeaveRequestDto(
                    request.getId(),
                    request.getEmployeeId(),
                    request.getEmployeeName(),
                    request.getRequestedByUserId(),
                    request.getLeaveType(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getHours(),
                    request.getStatus(),
                    request.getConflictCount() > 0,
                    request.getConflictCount(),
                    request.getConflictSummary(),
                    request.getSubmittedAt(),
                    request.getDecidedByUserId(),
                    request.getDecidedAt(),
                    request.getEmployeeNote(),
                    request.getManagerNote(),
                    request.getDecisionNote()
            );
        }
    }

    public record LeaveRequestCreate(
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hours,
            String note
    ) {
    }

    public record LeaveDecisionRequest(String note) {
    }

    public record LeaveBalanceDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            String leaveType,
            BigDecimal accruedHours,
            BigDecimal usedHours,
            BigDecimal pendingHours,
            BigDecimal availableHours,
            BigDecimal maxHours
    ) {
        static LeaveBalanceDto from(LeaveBalance balance) {
            var available = balance.getAccruedHours()
                    .subtract(balance.getUsedHours())
                    .subtract(balance.getPendingHours())
                    .max(BigDecimal.ZERO);
            return new LeaveBalanceDto(
                    balance.getId(),
                    balance.getEmployeeId(),
                    balance.getEmployeeName(),
                    balance.getLeaveType(),
                    balance.getAccruedHours(),
                    balance.getUsedHours(),
                    balance.getPendingHours(),
                    available,
                    balance.getMaxHours()
            );
        }
    }

    public record LeaveCalendarEntryDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hours,
            String status,
            boolean conflict,
            String conflictSummary
    ) {
        static LeaveCalendarEntryDto from(LeaveRequest request) {
            return new LeaveCalendarEntryDto(
                    request.getId(),
                    request.getEmployeeId(),
                    request.getEmployeeName(),
                    request.getLeaveType(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getHours(),
                    request.getStatus(),
                    request.getConflictCount() > 0,
                    request.getConflictSummary()
            );
        }
    }

    public record LeaveAccrualRunRequest(LocalDate asOfDate) {
    }

    public record LeaveAccrualRunResult(
            LocalDate accrualPeriod,
            int balancesUpdated,
            BigDecimal hoursAccrued
    ) {
    }
}
