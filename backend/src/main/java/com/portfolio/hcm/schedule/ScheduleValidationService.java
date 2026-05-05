package com.portfolio.hcm.schedule;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScheduleValidationService {
    public List<ScheduleRuleViolation> validate(List<ShiftDraft> shifts, SchedulePolicy policy) {
        return validate(null, shifts, policy);
    }

    public List<ScheduleRuleViolation> validate(LocalDate weekStartDate, List<ShiftDraft> shifts, SchedulePolicy policy) {
        var violations = new ArrayList<ScheduleRuleViolation>();
        var weekEndDate = weekStartDate == null ? null : weekStartDate.plusDays(6);

        for (var shift : shifts) {
            if (weekStartDate != null && (shift.shiftDate().isBefore(weekStartDate) || shift.shiftDate().isAfter(weekEndDate))) {
                violations.add(new ScheduleRuleViolation(
                        "OUTSIDE_WEEK",
                        "HIGH",
                        shift.displayName(),
                        "%s is outside the selected schedule week.".formatted(shift.displayName())
                ));
            }
            if (shift.startTime().equals(shift.endTime())) {
                violations.add(new ScheduleRuleViolation(
                        "INVALID_TIME",
                        "HIGH",
                        shift.displayName(),
                        "%s has a zero-length shift on %s.".formatted(shift.displayName(), shift.shiftDate())
                ));
            }
            if (shift.employeeId() == null || shift.openShift()) {
                violations.add(new ScheduleRuleViolation(
                        "OPEN_SHIFT",
                        "MEDIUM",
                        "Open Shift",
                        "An open shift remains on %s from %s to %s."
                                .formatted(shift.shiftDate(), shift.startTime(), shift.endTime())
                ));
            } else if (!shift.employeeExists()) {
                violations.add(new ScheduleRuleViolation(
                        "UNKNOWN_EMPLOYEE",
                        "HIGH",
                        shift.displayName(),
                        "%s references an employee that does not exist in this tenant.".formatted(shift.displayName())
                ));
            }
            if (!shift.departmentExists()) {
                violations.add(new ScheduleRuleViolation(
                        "UNKNOWN_DEPARTMENT",
                        "HIGH",
                        shift.displayName(),
                        "%s references a missing department.".formatted(shift.displayName())
                ));
            }
            if (!shift.locationExists()) {
                violations.add(new ScheduleRuleViolation(
                        "UNKNOWN_LOCATION",
                        "HIGH",
                        shift.displayName(),
                        "%s references a missing location.".formatted(shift.displayName())
                ));
            }
            if (shift.approvedLeaveConflict()) {
                violations.add(new ScheduleRuleViolation(
                        "APPROVED_LEAVE",
                        "HIGH",
                        shift.displayName(),
                        "%s is scheduled during approved leave on %s.".formatted(shift.displayName(), shift.shiftDate())
                ));
            }
        }

        var shiftsByEmployee = shifts.stream()
                .filter(shift -> shift.employeeId() != null)
                .filter(ShiftDraft::employeeExists)
                .filter(shift -> !shift.startTime().equals(shift.endTime()))
                .collect(Collectors.groupingBy(ShiftDraft::employeeId));

        shiftsByEmployee.forEach((employeeId, employeeShifts) -> {
            var sorted = employeeShifts.stream()
                    .sorted(Comparator.comparing(ShiftDraft::startsAt))
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                var current = sorted.get(i);
                if (i > 0) {
                    var previous = sorted.get(i - 1);
                    if (current.startsAt().isBefore(previous.endsAt())) {
                        violations.add(new ScheduleRuleViolation(
                                "OVERLAP",
                                "HIGH",
                                current.employeeName(),
                                "%s has overlapping shifts on %s.".formatted(current.employeeName(), current.shiftDate())
                        ));
                    }
                    var restHours = Duration.between(previous.endsAt(), current.startsAt()).toHours();
                    if (restHours >= 0 && restHours < policy.minimumRestHours()) {
                        violations.add(new ScheduleRuleViolation(
                                "MINIMUM_REST",
                                "MEDIUM",
                                current.employeeName(),
                                "%s has only %d hours of rest between shifts; policy requires %d."
                                        .formatted(current.employeeName(), restHours, policy.minimumRestHours())
                        ));
                    }
                }
            }

            var totalHours = sorted.stream()
                    .map(ShiftDraft::durationHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var cap = sorted.stream()
                    .findFirst()
                    .map(ShiftDraft::weeklyHourCap)
                    .orElse(policy.defaultWeeklyHourCap());
            if (totalHours.compareTo(cap) > 0) {
                violations.add(new ScheduleRuleViolation(
                        "WEEKLY_CAP",
                        "MEDIUM",
                        sorted.get(0).employeeName(),
                        "%s is scheduled for %s hours, exceeding the weekly cap of %s."
                                .formatted(sorted.get(0).employeeName(), totalHours, cap)
                ));
            }
        });

        return violations;
    }

    public LocalDate startOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public record SchedulePolicy(int minimumRestHours, BigDecimal defaultWeeklyHourCap) {
    }

    public record ShiftDraft(
            UUID employeeId,
            String employeeName,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal weeklyHourCap,
            UUID departmentId,
            UUID locationId,
            boolean employeeExists,
            boolean departmentExists,
            boolean locationExists,
            boolean approvedLeaveConflict,
            boolean openShift
    ) {
        public ShiftDraft(UUID employeeId, String employeeName, LocalDate shiftDate, LocalTime startTime, LocalTime endTime, BigDecimal weeklyHourCap) {
            this(employeeId, employeeName, shiftDate, startTime, endTime, weeklyHourCap, null, null, true, true, true, false, employeeId == null);
        }

        LocalDateTime startsAt() {
            return LocalDateTime.of(shiftDate, startTime);
        }

        LocalDateTime endsAt() {
            var endDate = endTime.isAfter(startTime) ? shiftDate : shiftDate.plusDays(1);
            return LocalDateTime.of(endDate, endTime);
        }

        BigDecimal durationHours() {
            return BigDecimal.valueOf(Duration.between(startsAt(), endsAt()).toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        String displayName() {
            return employeeName == null || employeeName.isBlank() ? "Open Shift" : employeeName;
        }
    }

    public record ScheduleRuleViolation(String type, String severity, String employeeName, String message) {
    }
}
