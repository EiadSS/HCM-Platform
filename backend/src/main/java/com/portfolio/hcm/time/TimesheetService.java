package com.portfolio.hcm.time;

import com.portfolio.hcm.audit.AuditLogDto;
import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.integration.WebhookEventService;
import com.portfolio.hcm.schedule.Shift;
import com.portfolio.hcm.schedule.ShiftRepository;
import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.portfolio.hcm.time.TimesheetDtos.ApprovalRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ChangeRequestRequest;
import static com.portfolio.hcm.time.TimesheetDtos.DecisionRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ManualTimeEntryRequest;
import static com.portfolio.hcm.time.TimesheetDtos.TimeBreakDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimeEntryDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetChangeRequestDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetDetailDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetDto;
import static com.portfolio.hcm.time.TimesheetDtos.ValidationIssueDto;
import static com.portfolio.hcm.time.TimesheetValidationService.BreakDraft;
import static com.portfolio.hcm.time.TimesheetValidationService.TimeEntryDraft;

@Service
public class TimesheetService {
    private static final BigDecimal REGULAR_WEEKLY_LIMIT = new BigDecimal("40.00");
    private static final ZoneId DEMO_ZONE = ZoneId.of("America/Toronto");

    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeBreakRepository timeBreakRepository;
    private final TimesheetChangeRequestRepository changeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final AuditLogRepository auditLogRepository;
    private final TimesheetValidationService validationService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final WebhookEventService webhookEventService;

    public TimesheetService(
            TimesheetRepository timesheetRepository,
            TimeEntryRepository timeEntryRepository,
            TimeBreakRepository timeBreakRepository,
            TimesheetChangeRequestRepository changeRequestRepository,
            EmployeeRepository employeeRepository,
            ShiftRepository shiftRepository,
            AuditLogRepository auditLogRepository,
            TimesheetValidationService validationService,
            CurrentUserService currentUserService,
            AuditService auditService,
            WebhookEventService webhookEventService
    ) {
        this.timesheetRepository = timesheetRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.timeBreakRepository = timeBreakRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.auditLogRepository = auditLogRepository;
        this.validationService = validationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.webhookEventService = webhookEventService;
    }

    @Transactional(readOnly = true)
    public List<TimesheetDto> list() {
        var user = currentUserService.requireUser();
        var timesheets = hasManagerAccess(user)
                ? timesheetRepository.findByTenantIdAndDeletedFalseOrderByWeekStartDateDesc(user.tenantId())
                : timesheetRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByWeekStartDateDesc(user.tenantId(), currentEmployee(user).getId());
        return timesheets.stream().map(TimesheetDto::from).toList();
    }

    @Transactional(readOnly = true)
    public TimesheetDetailDto detail(UUID id) {
        var timesheet = findForTenant(id);
        requireViewAccess(timesheet);
        return detailFor(timesheet);
    }

    @Transactional
    public TimesheetDetailDto currentUserWeek(LocalDate weekStartDate) {
        var user = currentUserService.requireUser();
        var employee = currentEmployee(user);
        var timesheet = findOrCreateForEmployeeWeek(employee, weekStartDate);
        return detailFor(timesheet);
    }

    @Transactional
    public TimesheetDetailDto addEntry(UUID timesheetId, ManualTimeEntryRequest request) {
        var timesheet = findForTenant(timesheetId);
        requireEditAccess(timesheet);
        requireEditable(timesheet);
        var before = snapshot(timesheet);
        var source = hasManagerAccess(currentUserService.requireUser()) ? TimeEntrySource.CORRECTION : TimeEntrySource.MANUAL;
        var entry = TimeEntry.builder()
                .tenantId(timesheet.getTenantId())
                .timesheetId(timesheet.getId())
                .employeeId(timesheet.getEmployeeId())
                .employeeName(timesheet.getEmployeeName())
                .entryDate(TimeTrackingService.entryDate(request.clockInAt()))
                .clockInAt(request.clockInAt())
                .clockOutAt(request.clockOutAt())
                .source(source)
                .status(request.clockOutAt() == null ? TimeEntryStatus.OPEN : TimeEntryStatus.COMPLETE)
                .note(request.note())
                .build();
        var saved = timeEntryRepository.save(entry);
        replaceManualBreak(timesheet.getTenantId(), saved.getId(), request, source);
        recalculate(timesheet);
        auditService.record("time.entry.created", "Timesheet", timesheet.getId(), before, snapshot(timesheet), "{\"entryId\":\"%s\"}".formatted(saved.getId()));
        return detailFor(timesheet);
    }

    @Transactional
    public TimesheetDetailDto updateEntry(UUID timesheetId, UUID entryId, ManualTimeEntryRequest request) {
        var timesheet = findForTenant(timesheetId);
        requireEditAccess(timesheet);
        requireEditable(timesheet);
        var entry = findEntry(timesheet, entryId);
        var before = entrySnapshot(entry);
        entry.setEntryDate(TimeTrackingService.entryDate(request.clockInAt()));
        entry.setClockInAt(request.clockInAt());
        entry.setClockOutAt(request.clockOutAt());
        entry.setStatus(request.clockOutAt() == null ? TimeEntryStatus.OPEN : TimeEntryStatus.COMPLETE);
        entry.setSource(hasManagerAccess(currentUserService.requireUser()) ? TimeEntrySource.CORRECTION : entry.getSource());
        entry.setNote(request.note());
        var saved = timeEntryRepository.save(entry);
        softDeleteBreaks(saved.getId());
        replaceManualBreak(timesheet.getTenantId(), saved.getId(), request, TimeBreakSource.CORRECTION);
        recalculate(timesheet);
        auditService.record("time.entry.updated", "TimeEntry", saved.getId(), before, entrySnapshot(saved), "{\"timesheetId\":\"%s\"}".formatted(timesheet.getId()));
        return detailFor(timesheet);
    }

    @Transactional
    public TimesheetDetailDto deleteEntry(UUID timesheetId, UUID entryId) {
        var timesheet = findForTenant(timesheetId);
        requireEditAccess(timesheet);
        requireEditable(timesheet);
        var entry = findEntry(timesheet, entryId);
        var before = entrySnapshot(entry);
        entry.setDeleted(true);
        timeEntryRepository.save(entry);
        softDeleteBreaks(entry.getId());
        recalculate(timesheet);
        auditService.record("time.entry.deleted", "TimeEntry", entry.getId(), before, null, "{\"timesheetId\":\"%s\"}".formatted(timesheet.getId()));
        return detailFor(timesheet);
    }

    @Transactional
    public TimesheetDto submit(UUID id) {
        var timesheet = findForTenant(id);
        requireEditAccess(timesheet);
        if (timesheet.getStatus() != TimesheetStatus.DRAFT && timesheet.getStatus() != TimesheetStatus.REJECTED && timesheet.getStatus() != TimesheetStatus.CHANGE_REQUESTED) {
            throw new BadRequestException("Only draft, rejected, or change-requested timesheets can be submitted");
        }
        var issues = validate(timesheet);
        issues.stream()
                .filter(issue -> issue.severity().equals("HIGH"))
                .findFirst()
                .ifPresent(issue -> {
                    throw new BadRequestException(issue.message());
                });
        var before = snapshot(timesheet);
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        timesheet.setSubmittedAt(Instant.now());
        var saved = timesheetRepository.save(timesheet);
        auditService.record("timesheet.submitted", "Timesheet", saved.getId(), before, snapshot(saved), "{\"source\":\"time-panel\"}");
        return TimesheetDto.from(saved);
    }

    @Transactional
    public TimesheetDto approve(UUID id, String note) {
        var timesheet = findForTenant(id);
        if (timesheet.getStatus() != TimesheetStatus.SUBMITTED && timesheet.getStatus() != TimesheetStatus.CHANGE_REQUESTED) {
            throw new BadRequestException("Only submitted or change-requested timesheets can be approved");
        }
        var before = snapshot(timesheet);
        var user = currentUserService.requireUser();
        timesheet.setStatus(TimesheetStatus.APPROVED);
        timesheet.setApprovedAt(Instant.now());
        timesheet.setApproverUserId(user.userId());
        timesheet.setManagerNote(note == null || note.isBlank() ? "Approved in demo workflow" : note);
        var saved = timesheetRepository.save(timesheet);
        auditService.record("timesheet.approved", "Timesheet", saved.getId(), before, snapshot(saved), "{\"source\":\"manager-dashboard\"}");
        webhookEventService.emit(saved.getTenantId(), "timesheet.approved", "Timesheet", saved.getId(), timesheetPayload(saved));
        return TimesheetDto.from(saved);
    }

    @Transactional
    public TimesheetDto reject(UUID id, String note) {
        var timesheet = findForTenant(id);
        if (timesheet.getStatus() != TimesheetStatus.SUBMITTED && timesheet.getStatus() != TimesheetStatus.CHANGE_REQUESTED) {
            throw new BadRequestException("Only submitted or change-requested timesheets can be rejected");
        }
        var before = snapshot(timesheet);
        timesheet.setStatus(TimesheetStatus.REJECTED);
        timesheet.setManagerNote(note == null || note.isBlank() ? "Rejected for correction" : note);
        var saved = timesheetRepository.save(timesheet);
        auditService.record("timesheet.rejected", "Timesheet", saved.getId(), before, snapshot(saved), "{\"source\":\"manager-dashboard\"}");
        return TimesheetDto.from(saved);
    }

    @Transactional
    public TimesheetChangeRequestDto requestChange(UUID id, ChangeRequestRequest request) {
        var timesheet = findForTenant(id);
        requireViewAccess(timesheet);
        if (timesheet.isLockedPayPeriod()) {
            throw new BadRequestException("Locked pay periods cannot be changed in the demo workflow");
        }
        if (timesheet.getStatus() != TimesheetStatus.APPROVED) {
            throw new BadRequestException("Only approved timesheets can receive change requests");
        }
        if (changeRequestRepository.existsByTimesheetIdAndStatusAndDeletedFalse(timesheet.getId(), TimesheetChangeRequestStatus.PENDING)) {
            throw new BadRequestException("A pending change request already exists for this timesheet");
        }
        var user = currentUserService.requireUser();
        var changeRequest = changeRequestRepository.save(TimesheetChangeRequest.builder()
                .tenantId(timesheet.getTenantId())
                .timesheetId(timesheet.getId())
                .requestedByUserId(user.userId())
                .requesterEmail(user.email())
                .reason(request.reason())
                .status(TimesheetChangeRequestStatus.PENDING)
                .build());
        auditService.record("timesheet.change.requested", "Timesheet", timesheet.getId(), null, request.reason(), "{\"changeRequestId\":\"%s\"}".formatted(changeRequest.getId()));
        return TimesheetChangeRequestDto.from(changeRequest);
    }

    @Transactional
    public TimesheetChangeRequestDto approveChangeRequest(UUID timesheetId, UUID requestId, DecisionRequest decision) {
        var timesheet = findForTenant(timesheetId);
        var request = findChangeRequest(timesheet, requestId);
        if (request.getStatus() != TimesheetChangeRequestStatus.PENDING) {
            throw new BadRequestException("Only pending change requests can be approved");
        }
        if (timesheet.isLockedPayPeriod()) {
            throw new BadRequestException("Locked pay periods cannot be reopened");
        }
        var before = snapshot(timesheet);
        var user = currentUserService.requireUser();
        request.setStatus(TimesheetChangeRequestStatus.APPROVED);
        request.setDecisionNote(decision == null ? null : decision.note());
        request.setDecidedByUserId(user.userId());
        request.setDecidedAt(Instant.now());
        timesheet.setStatus(TimesheetStatus.CHANGE_REQUESTED);
        timesheet.setManagerNote(decision == null || decision.note() == null || decision.note().isBlank() ? "Change request approved" : decision.note());
        changeRequestRepository.save(request);
        timesheetRepository.save(timesheet);
        auditService.record("timesheet.change.approved", "Timesheet", timesheet.getId(), before, snapshot(timesheet), "{\"changeRequestId\":\"%s\"}".formatted(request.getId()));
        return TimesheetChangeRequestDto.from(request);
    }

    @Transactional
    public TimesheetChangeRequestDto rejectChangeRequest(UUID timesheetId, UUID requestId, DecisionRequest decision) {
        var timesheet = findForTenant(timesheetId);
        var request = findChangeRequest(timesheet, requestId);
        if (request.getStatus() != TimesheetChangeRequestStatus.PENDING) {
            throw new BadRequestException("Only pending change requests can be rejected");
        }
        var user = currentUserService.requireUser();
        request.setStatus(TimesheetChangeRequestStatus.REJECTED);
        request.setDecisionNote(decision == null ? null : decision.note());
        request.setDecidedByUserId(user.userId());
        request.setDecidedAt(Instant.now());
        var saved = changeRequestRepository.save(request);
        auditService.record("timesheet.change.rejected", "Timesheet", timesheet.getId(), null, saved.getDecisionNote(), "{\"changeRequestId\":\"%s\"}".formatted(saved.getId()));
        return TimesheetChangeRequestDto.from(saved);
    }

    @Transactional
    public TimesheetDto lock(UUID id) {
        var timesheet = findForTenant(id);
        if (timesheet.getStatus() != TimesheetStatus.APPROVED) {
            throw new BadRequestException("Only approved timesheets can be locked");
        }
        var before = snapshot(timesheet);
        timesheet.setLockedPayPeriod(true);
        var saved = timesheetRepository.save(timesheet);
        auditService.record("timesheet.locked", "Timesheet", saved.getId(), before, snapshot(saved), "{\"source\":\"payroll\"}");
        return TimesheetDto.from(saved);
    }

    @Transactional
    public TimesheetDto unlock(UUID id) {
        var timesheet = findForTenant(id);
        var before = snapshot(timesheet);
        timesheet.setLockedPayPeriod(false);
        var saved = timesheetRepository.save(timesheet);
        auditService.record("timesheet.unlocked", "Timesheet", saved.getId(), before, snapshot(saved), "{\"source\":\"payroll\"}");
        return TimesheetDto.from(saved);
    }

    Timesheet findOrCreateForEmployeeWeek(Employee employee, LocalDate weekStartDate) {
        return timesheetRepository.findByTenantIdAndEmployeeIdAndWeekStartDateAndDeletedFalse(employee.getTenantId(), employee.getId(), weekStartDate)
                .orElseGet(() -> timesheetRepository.save(Timesheet.builder()
                        .tenantId(employee.getTenantId())
                        .employeeId(employee.getId())
                        .employeeName(employee.getFirstName() + " " + employee.getLastName())
                        .weekStartDate(weekStartDate)
                        .regularHours(BigDecimal.ZERO.setScale(2))
                        .overtimeHours(BigDecimal.ZERO.setScale(2))
                        .status(TimesheetStatus.DRAFT)
                        .lockedPayPeriod(false)
                        .build()));
    }

    Timesheet findForTenant(UUID id) {
        return timesheetRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUserService.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));
    }

    void requireEditable(Timesheet timesheet) {
        if (timesheet.isLockedPayPeriod()) {
            throw new BadRequestException("Pay period is locked; submit a change request instead of editing entries directly.");
        }
        if (timesheet.getStatus() == TimesheetStatus.APPROVED || timesheet.getStatus() == TimesheetStatus.SUBMITTED) {
            throw new BadRequestException("Timesheet must be draft, rejected, or change-requested before editing");
        }
    }

    void recalculate(Timesheet timesheet) {
        var total = timeEntryRepository.findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(timesheet.getId()).stream()
                .filter(entry -> entry.getClockOutAt() != null && entry.getClockOutAt().isAfter(entry.getClockInAt()))
                .map(this::paidHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        timesheet.setRegularHours(total.min(REGULAR_WEEKLY_LIMIT).setScale(2, RoundingMode.HALF_UP));
        timesheet.setOvertimeHours(total.subtract(REGULAR_WEEKLY_LIMIT).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        timesheetRepository.save(timesheet);
    }

    List<TimesheetValidationService.ValidationIssue> validate(Timesheet timesheet) {
        return validationService.validateDetailed(toDrafts(timesheet), timesheet.isLockedPayPeriod());
    }

    TimesheetDto dto(Timesheet timesheet) {
        return TimesheetDto.from(timesheet);
    }

    TimeEntryDto mapEntry(TimeEntry entry) {
        var breaks = timeBreakRepository.findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(entry.getId()).stream()
                .map(TimeBreakDto::from)
                .toList();
        return TimeEntryDto.from(entry, breaks, paidHours(entry));
    }

    TimeBreakDto mapBreak(TimeBreak timeBreak) {
        return TimeBreakDto.from(timeBreak);
    }

    Employee currentEmployee(AuthenticatedUser user) {
        return employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(user.tenantId(), user.userId())
                .orElseThrow(() -> new ForbiddenOperationException("Current user is not linked to an employee record"));
    }

    boolean hasManagerAccess(AuthenticatedUser user) {
        return user.hasRole(UserRole.MANAGER) || user.hasRole(UserRole.HR_ADMIN) || user.hasRole(UserRole.PAYROLL_ADMIN) || user.hasRole(UserRole.SYSTEM_ADMIN);
    }

    void requireViewAccess(Timesheet timesheet) {
        var user = currentUserService.requireUser();
        if (hasManagerAccess(user)) {
            return;
        }
        if (!timesheet.getEmployeeId().equals(currentEmployee(user).getId())) {
            throw new ForbiddenOperationException("You can only access your own timesheets");
        }
    }

    void requireEditAccess(Timesheet timesheet) {
        var user = currentUserService.requireUser();
        if (hasManagerAccess(user)) {
            return;
        }
        if (!timesheet.getEmployeeId().equals(currentEmployee(user).getId())) {
            throw new ForbiddenOperationException("You can only edit your own timesheets");
        }
    }

    private TimesheetDetailDto detailFor(Timesheet timesheet) {
        var entries = timeEntryRepository.findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(timesheet.getId()).stream()
                .map(this::mapEntry)
                .toList();
        var changeRequests = changeRequestRepository.findByTimesheetIdAndDeletedFalseOrderByCreatedAtDesc(timesheet.getId()).stream()
                .map(TimesheetChangeRequestDto::from)
                .toList();
        var validation = validate(timesheet).stream().map(ValidationIssueDto::from).toList();
        var history = auditLogRepository.findByTenantIdAndEntityTypeAndEntityIdAndDeletedFalseOrderByCreatedAtDesc(timesheet.getTenantId(), "Timesheet", timesheet.getId()).stream()
                .map(AuditLogDto::from)
                .toList();
        return new TimesheetDetailDto(TimesheetDto.from(timesheet), entries, changeRequests, validation, history);
    }

    private TimeEntry findEntry(Timesheet timesheet, UUID entryId) {
        return timeEntryRepository.findByIdAndTenantIdAndDeletedFalse(entryId, timesheet.getTenantId())
                .filter(entry -> entry.getTimesheetId().equals(timesheet.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));
    }

    private TimesheetChangeRequest findChangeRequest(Timesheet timesheet, UUID requestId) {
        return changeRequestRepository.findByIdAndTenantIdAndDeletedFalse(requestId, timesheet.getTenantId())
                .filter(request -> request.getTimesheetId().equals(timesheet.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found"));
    }

    private void replaceManualBreak(UUID tenantId, UUID entryId, ManualTimeEntryRequest request, TimeEntrySource source) {
        replaceManualBreak(tenantId, entryId, request, source == TimeEntrySource.CLOCK ? TimeBreakSource.CLOCK : source == TimeEntrySource.MANUAL ? TimeBreakSource.MANUAL : TimeBreakSource.CORRECTION);
    }

    private void replaceManualBreak(UUID tenantId, UUID entryId, ManualTimeEntryRequest request, TimeBreakSource source) {
        if (request.breakStartAt() == null && request.breakEndAt() == null) {
            return;
        }
        if (request.breakStartAt() == null || request.breakEndAt() == null) {
            throw new BadRequestException("Manual break start and end are both required");
        }
        var minutes = Duration.between(request.breakStartAt(), request.breakEndAt()).toMinutes();
        timeBreakRepository.save(TimeBreak.builder()
                .tenantId(tenantId)
                .timeEntryId(entryId)
                .breakStartAt(request.breakStartAt())
                .breakEndAt(request.breakEndAt())
                .durationMinutes((int) minutes)
                .source(source)
                .note(request.note())
                .build());
    }

    private void softDeleteBreaks(UUID entryId) {
        var breaks = timeBreakRepository.findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(entryId);
        breaks.forEach(timeBreak -> timeBreak.setDeleted(true));
        timeBreakRepository.saveAll(breaks);
    }

    private List<TimeEntryDraft> toDrafts(Timesheet timesheet) {
        var entries = timeEntryRepository.findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(timesheet.getId());
        if (entries.isEmpty()) {
            return List.of();
        }
        var breaksByEntry = timeBreakRepository.findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(entries.stream().map(TimeEntry::getId).toList()).stream()
                .collect(Collectors.groupingBy(TimeBreak::getTimeEntryId));
        return entries.stream()
                .map(entry -> {
                    var breaks = breaksByEntry.getOrDefault(entry.getId(), List.of());
                    var shift = scheduledShift(entry);
                    return new TimeEntryDraft(
                            entry.getId(),
                            entry.getEmployeeName(),
                            entry.getEntryDate(),
                            entry.getClockInAt(),
                            entry.getClockOutAt(),
                            breaks.stream().filter(timeBreak -> timeBreak.getDurationMinutes() != null).mapToLong(TimeBreak::getDurationMinutes).sum(),
                            breaks.stream().map(timeBreak -> new BreakDraft(timeBreak.getBreakStartAt(), timeBreak.getBreakEndAt())).toList(),
                            shift == null ? null : scheduledStart(shift),
                            shift == null ? null : scheduledEnd(shift),
                            breaks.stream().anyMatch(timeBreak -> timeBreak.getBreakEndAt() == null)
                    );
                })
                .toList();
    }

    private Shift scheduledShift(TimeEntry entry) {
        var shifts = shiftRepository.findByTenantIdAndEmployeeIdAndShiftDateAndDeletedFalseOrderByStartTimeAsc(entry.getTenantId(), entry.getEmployeeId(), entry.getEntryDate());
        if (entry.getShiftId() != null) {
            return shifts.stream().filter(shift -> shift.getId().equals(entry.getShiftId())).findFirst().orElse(null);
        }
        return shifts.stream().findFirst().orElse(null);
    }

    private Instant scheduledStart(Shift shift) {
        return LocalDateTime.of(shift.getShiftDate(), shift.getStartTime()).atZone(DEMO_ZONE).toInstant();
    }

    private Instant scheduledEnd(Shift shift) {
        var endDate = shift.getEndTime().isAfter(shift.getStartTime()) ? shift.getShiftDate() : shift.getShiftDate().plusDays(1);
        return LocalDateTime.of(endDate, shift.getEndTime()).atZone(DEMO_ZONE).toInstant();
    }

    private BigDecimal paidHours(TimeEntry entry) {
        if (entry.getClockOutAt() == null || !entry.getClockOutAt().isAfter(entry.getClockInAt())) {
            return BigDecimal.ZERO.setScale(2);
        }
        var grossMinutes = Duration.between(entry.getClockInAt(), entry.getClockOutAt()).toMinutes();
        var breakMinutes = timeBreakRepository.findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(entry.getId()).stream()
                .filter(timeBreak -> timeBreak.getDurationMinutes() != null)
                .mapToLong(TimeBreak::getDurationMinutes)
                .sum();
        return BigDecimal.valueOf(Math.max(0, grossMinutes - breakMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private String snapshot(Timesheet timesheet) {
        return """
                {"employeeName":"%s","weekStartDate":"%s","regularHours":%s,"overtimeHours":%s,"status":"%s","locked":%s}
                """.formatted(
                timesheet.getEmployeeName(),
                timesheet.getWeekStartDate(),
                timesheet.getRegularHours(),
                timesheet.getOvertimeHours(),
                timesheet.getStatus(),
                timesheet.isLockedPayPeriod()
        ).trim();
    }

    private java.util.Map<String, Object> timesheetPayload(Timesheet timesheet) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("timesheetId", timesheet.getId());
        payload.put("employeeId", timesheet.getEmployeeId());
        payload.put("employeeName", timesheet.getEmployeeName());
        payload.put("weekStartDate", timesheet.getWeekStartDate());
        payload.put("regularHours", timesheet.getRegularHours());
        payload.put("overtimeHours", timesheet.getOvertimeHours());
        payload.put("status", timesheet.getStatus().name());
        payload.put("approvedAt", timesheet.getApprovedAt());
        payload.put("lockedPayPeriod", timesheet.isLockedPayPeriod());
        return payload;
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
