package com.portfolio.hcm.schedule;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {
    private ScheduleDtos() {
    }

    public record ScheduleWeekDto(
            UUID id,
            LocalDate weekStartDate,
            String status,
            Instant publishedAt,
            UUID publishedByUserId,
            int shiftCount,
            int openShiftCount,
            int violationCount,
            int highSeverityCount
    ) {
        static ScheduleWeekDto from(ScheduleWeek week, int shiftCount, int openShiftCount, ValidationResultDto validation) {
            return new ScheduleWeekDto(
                    week.getId(),
                    week.getWeekStartDate(),
                    week.getStatus().name(),
                    week.getPublishedAt(),
                    week.getPublishedByUserId(),
                    shiftCount,
                    openShiftCount,
                    validation.violations().size(),
                    validation.highSeverityCount()
            );
        }
    }

    public record WeeklyScheduleDto(
            ScheduleWeekDto week,
            List<ShiftDto> shifts,
            List<ScheduleAlertDto> alerts,
            ValidationResultDto validation
    ) {
    }

    public record ScheduleAlertDto(
            UUID id,
            String employeeName,
            LocalDate weekStartDate,
            String alertType,
            String severity,
            String message,
            String status,
            Instant createdAt
    ) {
        static ScheduleAlertDto from(ScheduleAlert alert) {
            return new ScheduleAlertDto(alert.getId(), alert.getEmployeeName(), alert.getWeekStartDate(), alert.getAlertType(), alert.getSeverity(), alert.getMessage(), alert.getStatus(), alert.getCreatedAt());
        }
    }

    public record ShiftDto(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID departmentId,
            String departmentName,
            UUID locationId,
            String locationName,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            boolean published
    ) {
        static ShiftDto from(Shift shift) {
            return from(shift, null, null);
        }

        static ShiftDto from(Shift shift, String departmentName, String locationName) {
            return new ShiftDto(
                    shift.getId(),
                    shift.getEmployeeId(),
                    shift.getEmployeeName(),
                    shift.getDepartmentId(),
                    departmentName,
                    shift.getLocationId(),
                    locationName,
                    shift.getShiftDate(),
                    shift.getStartTime(),
                    shift.getEndTime(),
                    shift.getStatus(),
                    shift.isPublished()
            );
        }
    }

    public record ShiftRequest(
            UUID employeeId,
            @NotNull UUID departmentId,
            @NotNull UUID locationId,
            @NotNull LocalDate shiftDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime
    ) {
    }

    public record ScheduleViolationDto(
            String type,
            String severity,
            String employeeName,
            String message
    ) {
        static ScheduleViolationDto from(ScheduleValidationService.ScheduleRuleViolation violation) {
            return new ScheduleViolationDto(violation.type(), violation.severity(), violation.employeeName(), violation.message());
        }
    }

    public record ValidationResultDto(
            boolean valid,
            int highSeverityCount,
            List<ScheduleViolationDto> violations
    ) {
        static ValidationResultDto from(List<ScheduleValidationService.ScheduleRuleViolation> violations) {
            var mapped = violations.stream().map(ScheduleViolationDto::from).toList();
            var highCount = (int) mapped.stream().filter(violation -> violation.severity().equals("HIGH")).count();
            return new ValidationResultDto(highCount == 0, highCount, mapped);
        }

        static ValidationResultDto clean() {
            return new ValidationResultDto(true, 0, List.of());
        }
    }
}
