package com.portfolio.hcm.time;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.schedule.Shift;
import com.portfolio.hcm.schedule.ShiftRepository;
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.UUID;

import static com.portfolio.hcm.time.TimesheetDtos.BreakRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ClockRequest;
import static com.portfolio.hcm.time.TimesheetDtos.TimeBreakDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimeEntryDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimeStatusDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetDto;
import static com.portfolio.hcm.time.TimesheetDtos.ValidationIssueDto;

@Service
public class TimeTrackingService {
    private static final ZoneId DEMO_ZONE = ZoneId.of("America/Toronto");

    private final TimeEntryRepository timeEntryRepository;
    private final TimeBreakRepository timeBreakRepository;
    private final ShiftRepository shiftRepository;
    private final TimesheetService timesheetService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public TimeTrackingService(
            TimeEntryRepository timeEntryRepository,
            TimeBreakRepository timeBreakRepository,
            ShiftRepository shiftRepository,
            TimesheetService timesheetService,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.timeBreakRepository = timeBreakRepository;
        this.shiftRepository = shiftRepository;
        this.timesheetService = timesheetService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public TimeStatusDto status() {
        var user = currentUserService.requireUser();
        var employee = timesheetService.currentEmployee(user);
        var timesheet = timesheetService.findOrCreateForEmployeeWeek(employee, startOfWeek(LocalDate.now(DEMO_ZONE)));
        var activeEntry = timeEntryRepository.findFirstByTenantIdAndEmployeeIdAndClockOutAtIsNullAndDeletedFalseOrderByClockInAtDesc(user.tenantId(), employee.getId()).orElse(null);
        var activeBreak = activeEntry == null ? null : timeBreakRepository.findFirstByTenantIdAndTimeEntryIdAndBreakEndAtIsNullAndDeletedFalseOrderByBreakStartAtDesc(user.tenantId(), activeEntry.getId()).orElse(null);
        return new TimeStatusDto(
                TimesheetDto.from(timesheet),
                activeEntry == null ? null : timesheetService.mapEntry(activeEntry),
                activeBreak == null ? null : TimeBreakDto.from(activeBreak),
                timesheetService.validate(timesheet).stream().map(ValidationIssueDto::from).toList()
        );
    }

    @Transactional
    public TimeStatusDto clockIn(ClockRequest request) {
        var user = currentUserService.requireUser();
        var employee = timesheetService.currentEmployee(user);
        var occurredAt = occurredAt(request);
        timeEntryRepository.findFirstByTenantIdAndEmployeeIdAndClockOutAtIsNullAndDeletedFalseOrderByClockInAtDesc(user.tenantId(), employee.getId())
                .ifPresent(entry -> {
                    throw new BadRequestException("Clock out before starting another shift");
                });
        var timesheet = timesheetService.findOrCreateForEmployeeWeek(employee, startOfWeek(entryDate(occurredAt)));
        timesheetService.requireEditable(timesheet);
        var shift = matchingShift(employee, occurredAt);
        var entry = timeEntryRepository.save(TimeEntry.builder()
                .tenantId(user.tenantId())
                .timesheetId(timesheet.getId())
                .employeeId(employee.getId())
                .employeeName(employeeName(employee))
                .shiftId(shift == null ? null : shift.getId())
                .entryDate(entryDate(occurredAt))
                .clockInAt(occurredAt)
                .source(TimeEntrySource.CLOCK)
                .status(TimeEntryStatus.OPEN)
                .note(request == null ? null : request.note())
                .build());
        auditService.record("time.clock.in", "TimeEntry", entry.getId(), null, entrySnapshot(entry), "{\"timesheetId\":\"%s\"}".formatted(timesheet.getId()));
        return status();
    }

    @Transactional
    public TimeStatusDto clockOut(ClockRequest request) {
        var user = currentUserService.requireUser();
        var employee = timesheetService.currentEmployee(user);
        var entry = timeEntryRepository.findFirstByTenantIdAndEmployeeIdAndClockOutAtIsNullAndDeletedFalseOrderByClockInAtDesc(user.tenantId(), employee.getId())
                .orElseThrow(() -> new BadRequestException("No active clock-in found"));
        var timesheet = timesheetService.findForTenant(entry.getTimesheetId());
        timesheetService.requireEditable(timesheet);
        timeBreakRepository.findFirstByTenantIdAndTimeEntryIdAndBreakEndAtIsNullAndDeletedFalseOrderByBreakStartAtDesc(user.tenantId(), entry.getId())
                .ifPresent(timeBreak -> {
                    throw new BadRequestException("End the active break before clocking out");
                });
        var before = entrySnapshot(entry);
        entry.setClockOutAt(occurredAt(request));
        entry.setStatus(TimeEntryStatus.COMPLETE);
        entry.setNote(request == null || request.note() == null ? entry.getNote() : request.note());
        var saved = timeEntryRepository.save(entry);
        timesheetService.recalculate(timesheet);
        auditService.record("time.clock.out", "TimeEntry", saved.getId(), before, entrySnapshot(saved), "{\"timesheetId\":\"%s\"}".formatted(timesheet.getId()));
        return status();
    }

    @Transactional
    public TimeEntryDto startBreak(UUID entryId, BreakRequest request) {
        var entry = findAccessibleEntry(entryId);
        if (entry.getClockOutAt() != null) {
            throw new BadRequestException("Breaks can only be started on an open time entry");
        }
        timeBreakRepository.findFirstByTenantIdAndTimeEntryIdAndBreakEndAtIsNullAndDeletedFalseOrderByBreakStartAtDesc(entry.getTenantId(), entry.getId())
                .ifPresent(timeBreak -> {
                    throw new BadRequestException("An active break already exists for this entry");
                });
        var timeBreak = timeBreakRepository.save(TimeBreak.builder()
                .tenantId(entry.getTenantId())
                .timeEntryId(entry.getId())
                .breakStartAt(occurredAt(request))
                .source(TimeBreakSource.CLOCK)
                .note(request == null ? null : request.note())
                .build());
        auditService.record("time.break.started", "TimeEntry", entry.getId(), null, "{\"breakId\":\"%s\"}".formatted(timeBreak.getId()), "{\"source\":\"clock\"}");
        return timesheetService.mapEntry(entry);
    }

    @Transactional
    public TimeEntryDto endBreak(UUID breakId, BreakRequest request) {
        var user = currentUserService.requireUser();
        var timeBreak = timeBreakRepository.findByIdAndTenantIdAndDeletedFalse(breakId, user.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Break not found"));
        var entry = findAccessibleEntry(timeBreak.getTimeEntryId());
        if (timeBreak.getBreakEndAt() != null) {
            throw new BadRequestException("Break is already ended");
        }
        var before = "{\"breakStartAt\":\"%s\"}".formatted(timeBreak.getBreakStartAt());
        var end = occurredAt(request);
        timeBreak.setBreakEndAt(end);
        timeBreak.setDurationMinutes((int) Duration.between(timeBreak.getBreakStartAt(), end).toMinutes());
        timeBreak.setNote(request == null || request.note() == null ? timeBreak.getNote() : request.note());
        var saved = timeBreakRepository.save(timeBreak);
        timesheetService.recalculate(timesheetService.findForTenant(entry.getTimesheetId()));
        auditService.record("time.break.ended", "TimeEntry", entry.getId(), before, "{\"durationMinutes\":%d}".formatted(saved.getDurationMinutes()), "{\"breakId\":\"%s\"}".formatted(saved.getId()));
        return timesheetService.mapEntry(entry);
    }

    private TimeEntry findAccessibleEntry(UUID entryId) {
        var user = currentUserService.requireUser();
        var entry = timeEntryRepository.findByIdAndTenantIdAndDeletedFalse(entryId, user.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));
        var timesheet = timesheetService.findForTenant(entry.getTimesheetId());
        timesheetService.requireEditAccess(timesheet);
        timesheetService.requireEditable(timesheet);
        return entry;
    }

    private Shift matchingShift(Employee employee, Instant occurredAt) {
        return shiftRepository.findByTenantIdAndEmployeeIdAndShiftDateAndDeletedFalseOrderByStartTimeAsc(employee.getTenantId(), employee.getId(), entryDate(occurredAt)).stream()
                .filter(shift -> shift.isPublished() || shift.getShiftDate().equals(entryDate(occurredAt)))
                .min(Comparator.comparing(Shift::getStartTime))
                .orElse(null);
    }

    private Instant occurredAt(ClockRequest request) {
        return request == null || request.occurredAt() == null ? Instant.now() : request.occurredAt();
    }

    private Instant occurredAt(BreakRequest request) {
        return request == null || request.occurredAt() == null ? Instant.now() : request.occurredAt();
    }

    static LocalDate entryDate(Instant instant) {
        return instant.atZone(DEMO_ZONE).toLocalDate();
    }

    static LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private String employeeName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    private String entrySnapshot(TimeEntry entry) {
        return """
                {"employeeName":"%s","entryDate":"%s","clockInAt":"%s","clockOutAt":"%s","status":"%s"}
                """.formatted(
                entry.getEmployeeName(),
                entry.getEntryDate(),
                entry.getClockInAt(),
                entry.getClockOutAt(),
                entry.getStatus()
        ).trim();
    }
}
