package com.portfolio.hcm.time;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TimesheetValidationService {
    private static final long SCHEDULE_GRACE_MINUTES = 5;

    public List<String> validate(List<TimeEntryDraft> entries, boolean lockedPayPeriod) {
        return validateDetailed(entries, lockedPayPeriod).stream()
                .map(ValidationIssue::message)
                .toList();
    }

    public List<ValidationIssue> validateDetailed(List<TimeEntryDraft> entries, boolean lockedPayPeriod) {
        var issues = new ArrayList<ValidationIssue>();
        if (lockedPayPeriod) {
            issues.add(new ValidationIssue("LOCKED_PERIOD", "HIGH", "Pay period is locked; submit a change request instead of editing entries directly."));
        }

        var completeEntries = new ArrayList<TimeEntryDraft>();
        for (var entry : entries) {
            if (entry.clockOut() == null) {
                var type = entry.entryDate() != null && entry.entryDate().isBefore(LocalDate.now()) ? "MISSED_PUNCH" : "OPEN_PUNCH";
                issues.add(new ValidationIssue(type, "HIGH", "Missed punch detected for " + entry.employeeName() + "."));
                continue;
            }
            if (!entry.clockOut().isAfter(entry.clockIn())) {
                issues.add(new ValidationIssue("INVALID_TIME_RANGE", "HIGH", "Clock-out must be after clock-in for " + entry.employeeName() + "."));
                continue;
            }

            var shiftMinutes = Duration.between(entry.clockIn(), entry.clockOut()).toMinutes();
            if (entry.unpaidBreakMinutes() < 0 || entry.unpaidBreakMinutes() > shiftMinutes) {
                issues.add(new ValidationIssue("INVALID_BREAK", "HIGH", "Unpaid break duration is invalid for " + entry.employeeName() + "."));
            }
            if (entry.activeBreak()) {
                issues.add(new ValidationIssue("ACTIVE_BREAK", "HIGH", "End the active break for " + entry.employeeName() + " before submitting."));
            }
            issues.addAll(validateBreaks(entry));
            if (entry.scheduledStart() != null && entry.clockIn().isAfter(entry.scheduledStart().plus(Duration.ofMinutes(SCHEDULE_GRACE_MINUTES)))) {
                issues.add(new ValidationIssue("LATE_ARRIVAL", "MEDIUM", entry.employeeName() + " clocked in after the scheduled start time."));
            }
            if (entry.scheduledEnd() != null && entry.clockOut().isBefore(entry.scheduledEnd().minus(Duration.ofMinutes(SCHEDULE_GRACE_MINUTES)))) {
                issues.add(new ValidationIssue("EARLY_DEPARTURE", "MEDIUM", entry.employeeName() + " clocked out before the scheduled end time."));
            }
            completeEntries.add(entry);
        }

        completeEntries.stream()
                .sorted(Comparator.comparing(TimeEntryDraft::clockIn))
                .reduce((previous, current) -> {
                    if (current.clockIn().isBefore(previous.clockOut())) {
                        issues.add(new ValidationIssue("OVERLAPPING_ENTRIES", "HIGH", "Overlapping time entries detected for " + current.employeeName() + "."));
                    }
                    return current.clockOut().isAfter(previous.clockOut()) ? current : previous;
                });

        return issues;
    }

    private List<ValidationIssue> validateBreaks(TimeEntryDraft entry) {
        var issues = new ArrayList<ValidationIssue>();
        var completeBreaks = entry.breaks().stream()
                .filter(timeBreak -> timeBreak.start() != null && timeBreak.end() != null)
                .sorted(Comparator.comparing(BreakDraft::start))
                .toList();
        for (var timeBreak : entry.breaks()) {
            if (timeBreak.start() == null) {
                issues.add(new ValidationIssue("INVALID_BREAK", "HIGH", "Break start is required for " + entry.employeeName() + "."));
                continue;
            }
            if (timeBreak.end() == null) {
                continue;
            }
            if (!timeBreak.end().isAfter(timeBreak.start())) {
                issues.add(new ValidationIssue("INVALID_BREAK", "HIGH", "Break end must be after break start for " + entry.employeeName() + "."));
            }
            if (timeBreak.start().isBefore(entry.clockIn()) || timeBreak.end().isAfter(entry.clockOut())) {
                issues.add(new ValidationIssue("INVALID_BREAK", "HIGH", "Break must fall inside the time entry for " + entry.employeeName() + "."));
            }
        }
        for (int i = 1; i < completeBreaks.size(); i++) {
            var previous = completeBreaks.get(i - 1);
            var current = completeBreaks.get(i);
            if (current.start().isBefore(previous.end())) {
                issues.add(new ValidationIssue("OVERLAPPING_BREAKS", "HIGH", "Overlapping breaks detected for " + entry.employeeName() + "."));
            }
        }
        return issues;
    }

    public record TimeEntryDraft(
            UUID id,
            String employeeName,
            LocalDate entryDate,
            Instant clockIn,
            Instant clockOut,
            long unpaidBreakMinutes,
            List<BreakDraft> breaks,
            Instant scheduledStart,
            Instant scheduledEnd,
            boolean activeBreak
    ) {
        public TimeEntryDraft(String employeeName, Instant clockIn, Instant clockOut, long unpaidBreakMinutes) {
            this(null, employeeName, null, clockIn, clockOut, unpaidBreakMinutes, List.of(), null, null, false);
        }
    }

    public record BreakDraft(Instant start, Instant end) {
    }

    public record ValidationIssue(String type, String severity, String message) {
        boolean blocking() {
            return severity.equals("HIGH");
        }
    }
}
