package com.portfolio.hcm.schedule;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.leave.LeaveRequest;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.org.Department;
import com.portfolio.hcm.org.DepartmentRepository;
import com.portfolio.hcm.org.Location;
import com.portfolio.hcm.org.LocationRepository;
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.portfolio.hcm.schedule.ScheduleDtos.ScheduleAlertDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ScheduleWeekDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftRequest;
import static com.portfolio.hcm.schedule.ScheduleDtos.ValidationResultDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.WeeklyScheduleDto;
import static com.portfolio.hcm.schedule.ScheduleValidationService.SchedulePolicy;
import static com.portfolio.hcm.schedule.ScheduleValidationService.ShiftDraft;

@Service
public class ScheduleService {
    private static final int MINIMUM_REST_HOURS = 10;
    private static final BigDecimal DEFAULT_WEEKLY_HOUR_CAP = new BigDecimal("40");

    private final ScheduleWeekRepository scheduleWeekRepository;
    private final ShiftRepository shiftRepository;
    private final ScheduleAlertRepository scheduleAlertRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleValidationService validationService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ScheduleService(
            ScheduleWeekRepository scheduleWeekRepository,
            ShiftRepository shiftRepository,
            ScheduleAlertRepository scheduleAlertRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            LeaveRequestRepository leaveRequestRepository,
            ScheduleValidationService validationService,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.scheduleWeekRepository = scheduleWeekRepository;
        this.shiftRepository = shiftRepository;
        this.scheduleAlertRepository = scheduleAlertRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.validationService = validationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ShiftDto> allShifts() {
        var tenantId = currentUserService.tenantId();
        var context = referenceContext(tenantId);
        return shiftRepository.findByTenantIdAndDeletedFalseOrderByShiftDateAscStartTimeAsc(tenantId).stream()
                .map(shift -> mapShift(shift, context))
                .toList();
    }

    @Transactional
    public List<ScheduleWeekDto> listWeeks(LocalDate from, LocalDate to) {
        var tenantId = currentUserService.tenantId();
        var start = from == null ? LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1) : from.with(DayOfWeek.MONDAY);
        var end = to == null ? start.plusWeeks(4) : to.with(DayOfWeek.MONDAY);
        return scheduleWeekRepository.findByTenantIdAndWeekStartDateBetweenAndDeletedFalseOrderByWeekStartDateAsc(tenantId, start, end).stream()
                .map(week -> {
                    var shifts = weekShifts(tenantId, week.getWeekStartDate());
                    var validation = validateInMemory(tenantId, week.getWeekStartDate(), shifts);
                    return ScheduleWeekDto.from(week, shifts.size(), openShiftCount(shifts), validation);
                })
                .toList();
    }

    @Transactional
    public WeeklyScheduleDto getWeek(LocalDate weekStartDate) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        return buildWeeklySchedule(tenantId, week, false);
    }

    @Transactional
    public WeeklyScheduleDto createShift(LocalDate weekStartDate, ShiftRequest request) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        requireDateInWeek(week.getWeekStartDate(), request.shiftDate());
        reopenDraftWeek(tenantId, week);

        var shift = new Shift();
        shift.setTenantId(tenantId);
        applyRequest(tenantId, shift, request);
        var saved = shiftRepository.save(shift);
        auditService.record("schedule.shift.created", "Shift", saved.getId(), null, shiftSnapshot(saved), "{\"source\":\"manager-schedule\"}");
        return buildWeeklySchedule(tenantId, week, true);
    }

    @Transactional
    public WeeklyScheduleDto updateShift(LocalDate weekStartDate, UUID shiftId, ShiftRequest request) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        requireDateInWeek(week.getWeekStartDate(), request.shiftDate());
        var shift = findShiftInWeek(tenantId, week.getWeekStartDate(), shiftId);
        var before = shiftSnapshot(shift);
        reopenDraftWeek(tenantId, week);

        applyRequest(tenantId, shift, request);
        shift.setPublished(false);
        var saved = shiftRepository.save(shift);
        auditService.record("schedule.shift.updated", "Shift", saved.getId(), before, shiftSnapshot(saved), "{\"source\":\"manager-schedule\"}");
        return buildWeeklySchedule(tenantId, week, true);
    }

    @Transactional
    public WeeklyScheduleDto deleteShift(LocalDate weekStartDate, UUID shiftId) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        var shift = findShiftInWeek(tenantId, week.getWeekStartDate(), shiftId);
        var before = shiftSnapshot(shift);
        reopenDraftWeek(tenantId, week);

        shift.setDeleted(true);
        shift.setPublished(false);
        var saved = shiftRepository.save(shift);
        auditService.record("schedule.shift.deleted", "Shift", saved.getId(), before, null, "{\"source\":\"manager-schedule\"}");
        return buildWeeklySchedule(tenantId, week, true);
    }

    @Transactional
    public WeeklyScheduleDto validateWeek(LocalDate weekStartDate) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        return buildWeeklySchedule(tenantId, week, true);
    }

    @Transactional
    public WeeklyScheduleDto publishWeek(LocalDate weekStartDate) {
        var tenantId = currentUserService.tenantId();
        var week = getOrCreateWeek(tenantId, requireMonday(weekStartDate));
        var validation = persistValidationAlerts(tenantId, week.getWeekStartDate(), weekShifts(tenantId, week.getWeekStartDate()));
        if (validation.highSeverityCount() > 0) {
            throw new BadRequestException("Resolve high-severity schedule issues before publishing");
        }

        var user = currentUserService.requireUser();
        week.setStatus(ScheduleWeekStatus.PUBLISHED);
        week.setPublishedAt(Instant.now());
        week.setPublishedByUserId(user.userId());
        scheduleWeekRepository.save(week);

        var shifts = weekShifts(tenantId, week.getWeekStartDate());
        shifts.forEach(shift -> shift.setPublished(true));
        shiftRepository.saveAll(shifts);

        auditService.record(
                "schedule.published",
                "ScheduleWeek",
                week.getId(),
                null,
                "{\"weekStart\":\"%s\",\"warnings\":%d}".formatted(week.getWeekStartDate(), validation.violations().size()),
                "{\"source\":\"manager-schedule\"}"
        );
        return buildWeeklySchedule(tenantId, week, false);
    }

    private WeeklyScheduleDto buildWeeklySchedule(UUID tenantId, ScheduleWeek week, boolean persistAlerts) {
        var shifts = weekShifts(tenantId, week.getWeekStartDate());
        var validation = persistAlerts
                ? persistValidationAlerts(tenantId, week.getWeekStartDate(), shifts)
                : validateInMemory(tenantId, week.getWeekStartDate(), shifts);
        var context = referenceContext(tenantId);
        var alerts = scheduleAlertRepository.findByTenantIdAndWeekStartDateAndDeletedFalseOrderByCreatedAtDesc(tenantId, week.getWeekStartDate()).stream()
                .map(ScheduleAlertDto::from)
                .toList();
        return new WeeklyScheduleDto(
                ScheduleWeekDto.from(week, shifts.size(), openShiftCount(shifts), validation),
                shifts.stream().map(shift -> mapShift(shift, context)).toList(),
                alerts,
                validation
        );
    }

    private ValidationResultDto validateInMemory(UUID tenantId, LocalDate weekStartDate, List<Shift> shifts) {
        var violations = validationService.validate(
                weekStartDate,
                toDrafts(tenantId, shifts),
                new SchedulePolicy(MINIMUM_REST_HOURS, DEFAULT_WEEKLY_HOUR_CAP)
        );
        return ValidationResultDto.from(violations);
    }

    private ValidationResultDto persistValidationAlerts(UUID tenantId, LocalDate weekStartDate, List<Shift> shifts) {
        var validation = validateInMemory(tenantId, weekStartDate, shifts);
        scheduleAlertRepository.deleteByTenantIdAndWeekStartDate(tenantId, weekStartDate);
        var alerts = validation.violations().stream()
                .map(violation -> ScheduleAlert.builder()
                        .tenantId(tenantId)
                        .employeeName(violation.employeeName())
                        .weekStartDate(weekStartDate)
                        .alertType(violation.type())
                        .severity(violation.severity())
                        .message(violation.message())
                        .status("OPEN")
                        .build())
                .toList();
        scheduleAlertRepository.saveAll(alerts);
        return validation;
    }

    private List<ShiftDraft> toDrafts(UUID tenantId, List<Shift> shifts) {
        var employees = employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        var departments = departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        var locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        var approvedLeaves = leaveRequestRepository.findByTenantIdAndDeletedFalseOrderByStartDateAsc(tenantId).stream()
                .filter(leave -> leave.getStatus().equals("APPROVED"))
                .toList();

        return shifts.stream()
                .map(shift -> {
                    var employee = shift.getEmployeeId() == null ? null : employees.get(shift.getEmployeeId());
                    return new ShiftDraft(
                            shift.getEmployeeId(),
                            employee == null ? shift.getEmployeeName() : employee.getFirstName() + " " + employee.getLastName(),
                            shift.getShiftDate(),
                            shift.getStartTime(),
                            shift.getEndTime(),
                            employee == null ? DEFAULT_WEEKLY_HOUR_CAP : employee.getWeeklyHourCap(),
                            shift.getDepartmentId(),
                            shift.getLocationId(),
                            shift.getEmployeeId() == null || employee != null,
                            shift.getDepartmentId() != null && departments.containsKey(shift.getDepartmentId()),
                            shift.getLocationId() != null && locations.containsKey(shift.getLocationId()),
                            employee != null && conflictsWithApprovedLeave(employee.getId(), shift.getShiftDate(), approvedLeaves),
                            shift.getEmployeeId() == null
                    );
                })
                .toList();
    }

    private boolean conflictsWithApprovedLeave(UUID employeeId, LocalDate shiftDate, List<LeaveRequest> approvedLeaves) {
        return approvedLeaves.stream()
                .filter(leave -> leave.getEmployeeId().equals(employeeId))
                .anyMatch(leave -> !shiftDate.isBefore(leave.getStartDate()) && !shiftDate.isAfter(leave.getEndDate()));
    }

    private void applyRequest(UUID tenantId, Shift shift, ShiftRequest request) {
        var departments = departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        if (!departments.containsKey(request.departmentId())) {
            throw new BadRequestException("Department does not exist in this tenant");
        }
        var locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        if (!locations.containsKey(request.locationId())) {
            throw new BadRequestException("Location does not exist in this tenant");
        }

        if (request.employeeId() == null) {
            shift.setEmployeeId(null);
            shift.setEmployeeName("Open Shift");
            shift.setStatus("OPEN");
        } else {
            var employee = employeeRepository.findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId)
                    .orElseThrow(() -> new BadRequestException("Employee does not exist in this tenant"));
            shift.setEmployeeId(employee.getId());
            shift.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
            shift.setStatus("ASSIGNED");
        }
        shift.setDepartmentId(request.departmentId());
        shift.setLocationId(request.locationId());
        shift.setShiftDate(request.shiftDate());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setPublished(false);
    }

    private void reopenDraftWeek(UUID tenantId, ScheduleWeek week) {
        if (week.getStatus() == ScheduleWeekStatus.PUBLISHED) {
            week.setStatus(ScheduleWeekStatus.DRAFT);
            week.setPublishedAt(null);
            week.setPublishedByUserId(null);
            scheduleWeekRepository.save(week);
        }
        var shifts = weekShifts(tenantId, week.getWeekStartDate());
        shifts.forEach(shift -> shift.setPublished(false));
        shiftRepository.saveAll(shifts);
    }

    private ScheduleWeek getOrCreateWeek(UUID tenantId, LocalDate weekStartDate) {
        return scheduleWeekRepository.findByTenantIdAndWeekStartDateAndDeletedFalse(tenantId, weekStartDate)
                .orElseGet(() -> scheduleWeekRepository.save(ScheduleWeek.builder()
                        .tenantId(tenantId)
                        .weekStartDate(weekStartDate)
                        .status(ScheduleWeekStatus.DRAFT)
                        .build()));
    }

    private Shift findShiftInWeek(UUID tenantId, LocalDate weekStartDate, UUID shiftId) {
        var shift = shiftRepository.findByIdAndTenantIdAndDeletedFalse(shiftId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getShiftDate().isBefore(weekStartDate) || shift.getShiftDate().isAfter(weekStartDate.plusDays(6))) {
            throw new ResourceNotFoundException("Shift not found in this schedule week");
        }
        return shift;
    }

    private List<Shift> weekShifts(UUID tenantId, LocalDate weekStartDate) {
        return shiftRepository.findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(tenantId, weekStartDate, weekStartDate.plusDays(6));
    }

    private int openShiftCount(List<Shift> shifts) {
        return (int) shifts.stream().filter(shift -> shift.getEmployeeId() == null).count();
    }

    private ReferenceContext referenceContext(UUID tenantId) {
        var departments = departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        var locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
        return new ReferenceContext(departments, locations);
    }

    private ShiftDto mapShift(Shift shift, ReferenceContext context) {
        return ShiftDto.from(shift, context.departments().get(shift.getDepartmentId()), context.locations().get(shift.getLocationId()));
    }

    private LocalDate requireMonday(LocalDate weekStartDate) {
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BadRequestException("Schedule week start date must be a Monday");
        }
        return weekStartDate;
    }

    private void requireDateInWeek(LocalDate weekStartDate, LocalDate shiftDate) {
        if (shiftDate.isBefore(weekStartDate) || shiftDate.isAfter(weekStartDate.plusDays(6))) {
            throw new BadRequestException("Shift date must be inside the selected schedule week");
        }
    }

    private String shiftSnapshot(Shift shift) {
        return """
                {"employeeName":"%s","shiftDate":"%s","startTime":"%s","endTime":"%s","status":"%s","published":%s}
                """.formatted(
                shift.getEmployeeName(),
                shift.getShiftDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getStatus(),
                shift.isPublished()
        ).trim();
    }

    private record ReferenceContext(Map<UUID, String> departments, Map<UUID, String> locations) {
    }
}
