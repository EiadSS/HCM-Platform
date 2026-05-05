package com.portfolio.hcm.leave;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.schedule.Shift;
import com.portfolio.hcm.schedule.ShiftRepository;
import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunResult;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveBalanceDto;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveCalendarEntryDto;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveDecisionRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestCreate;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestDto;

@Service
public class LeaveService {
    static final String VACATION = "VACATION";
    static final String SICK = "SICK";
    static final String UNPAID = "UNPAID";
    static final String PENDING = "PENDING";
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveAccrualRuleRepository leaveAccrualRuleRepository;
    private final LeaveBalanceEventRepository leaveBalanceEventRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public LeaveService(
            LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveAccrualRuleRepository leaveAccrualRuleRepository,
            LeaveBalanceEventRepository leaveBalanceEventRepository,
            EmployeeRepository employeeRepository,
            ShiftRepository shiftRepository,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveAccrualRuleRepository = leaveAccrualRuleRepository;
        this.leaveBalanceEventRepository = leaveBalanceEventRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> list(LocalDate from, LocalDate to, boolean mine) {
        var user = currentUserService.requireUser();
        if (mine || !hasManagerAccess(user)) {
            var employee = currentEmployee(user);
            return leaveRequestRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByStartDateDesc(user.tenantId(), employee.getId()).stream()
                    .filter(request -> overlaps(request, from, to))
                    .map(LeaveRequestDto::from)
                    .toList();
        }
        if (from != null || to != null) {
            var start = from == null ? LocalDate.of(1900, 1, 1) : from;
            var end = to == null ? LocalDate.of(2999, 12, 31) : to;
            return leaveRequestRepository.findByTenantIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualAndDeletedFalseOrderByStartDateAsc(user.tenantId(), start, end).stream()
                    .map(LeaveRequestDto::from)
                    .toList();
        }
        return leaveRequestRepository.findByTenantIdAndDeletedFalseOrderByStartDateAsc(user.tenantId()).stream()
                .map(LeaveRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> balances(UUID employeeId) {
        var user = currentUserService.requireUser();
        if (employeeId != null) {
            var employee = employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, user.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            requireEmployeeBalanceAccess(user, employee);
            return leaveBalanceRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByLeaveTypeAsc(user.tenantId(), employee.getId()).stream()
                    .map(LeaveBalanceDto::from)
                    .toList();
        }
        if (!hasManagerAccess(user)) {
            return leaveBalanceRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByLeaveTypeAsc(user.tenantId(), currentEmployee(user).getId()).stream()
                    .map(LeaveBalanceDto::from)
                    .toList();
        }
        return leaveBalanceRepository.findByTenantIdAndDeletedFalseOrderByEmployeeNameAscLeaveTypeAsc(user.tenantId()).stream()
                .map(LeaveBalanceDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveCalendarEntryDto> calendar(LocalDate from, LocalDate to) {
        var start = from == null ? LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()) : from;
        var end = to == null ? start.with(TemporalAdjusters.lastDayOfMonth()) : to;
        return list(start, end, false).stream()
                .map(dto -> new LeaveCalendarEntryDto(dto.id(), dto.employeeId(), dto.employeeName(), dto.leaveType(), dto.startDate(), dto.endDate(), dto.hours(), dto.status(), dto.conflict(), dto.conflictSummary()))
                .toList();
    }

    @Transactional
    public LeaveRequestDto create(LeaveRequestCreate request) {
        var user = currentUserService.requireUser();
        var employee = currentEmployee(user);
        var leaveType = normalizeType(request.leaveType());
        var hours = hours(request.hours());
        validateDates(request.startDate(), request.endDate());
        if (hours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Leave request hours must be greater than zero");
        }

        LeaveBalance balance = null;
        if (requiresBalance(leaveType)) {
            balance = leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(user.tenantId(), employee.getId(), leaveType)
                    .orElseThrow(() -> new BadRequestException("No " + leaveType.toLowerCase(Locale.ROOT) + " balance exists for " + fullName(employee)));
            if (available(balance).compareTo(hours) < 0) {
                throw new BadRequestException("Insufficient " + leaveType.toLowerCase(Locale.ROOT) + " balance. Available: " + available(balance) + " hours");
            }
        }

        var conflict = conflictFor(employee.getId(), request.startDate(), request.endDate());
        var leaveRequest = LeaveRequest.builder()
                .tenantId(user.tenantId())
                .employeeId(employee.getId())
                .employeeName(fullName(employee))
                .requestedByUserId(user.userId())
                .leaveType(leaveType)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .hours(hours)
                .status(PENDING)
                .submittedAt(Instant.now())
                .employeeNote(request.note())
                .managerNote(request.note())
                .conflictCount(conflict.count())
                .conflictSummary(conflict.summary())
                .build();
        var saved = leaveRequestRepository.save(leaveRequest);
        if (balance != null) {
            balance.setPendingHours(hours(balance.getPendingHours().add(hours)));
            leaveBalanceRepository.save(balance);
            recordBalanceEvent(balance, saved.getId(), "REQUEST_RESERVED", LocalDate.now(), null, hours, "Pending leave request reserved balance");
        }
        auditService.record(user, "leave.request.submitted", "LeaveRequest", saved.getId(), null, snapshot(saved), "{\"conflicts\":%d}".formatted(saved.getConflictCount()));
        return LeaveRequestDto.from(saved);
    }

    @Transactional
    public LeaveRequestDto approve(UUID id, LeaveDecisionRequest request) {
        var user = currentUserService.requireUser();
        requireManager(user);
        var leaveRequest = findRequest(id, user.tenantId());
        requirePending(leaveRequest);
        if (requiresBalance(leaveRequest.getLeaveType())) {
            var balance = balanceFor(leaveRequest);
            balance.setPendingHours(hours(balance.getPendingHours().subtract(leaveRequest.getHours()).max(BigDecimal.ZERO)));
            balance.setUsedHours(hours(balance.getUsedHours().add(leaveRequest.getHours())));
            leaveBalanceRepository.save(balance);
            recordBalanceEvent(balance, leaveRequest.getId(), "REQUEST_APPROVED_USED", LocalDate.now(), null, leaveRequest.getHours(), "Approved leave moved from pending to used");
        }
        var before = snapshot(leaveRequest);
        leaveRequest.setStatus(APPROVED);
        leaveRequest.setDecidedByUserId(user.userId());
        leaveRequest.setDecidedAt(Instant.now());
        leaveRequest.setDecisionNote(request == null ? null : request.note());
        leaveRequest.setManagerNote(request == null ? leaveRequest.getManagerNote() : request.note());
        var saved = leaveRequestRepository.save(leaveRequest);
        auditService.record(user, "leave.request.approved", "LeaveRequest", saved.getId(), before, snapshot(saved), "{\"leaveType\":\"%s\",\"hours\":%s}".formatted(saved.getLeaveType(), saved.getHours()));
        return LeaveRequestDto.from(saved);
    }

    @Transactional
    public LeaveRequestDto reject(UUID id, LeaveDecisionRequest request) {
        var user = currentUserService.requireUser();
        requireManager(user);
        var leaveRequest = findRequest(id, user.tenantId());
        requirePending(leaveRequest);
        if (requiresBalance(leaveRequest.getLeaveType())) {
            var balance = balanceFor(leaveRequest);
            balance.setPendingHours(hours(balance.getPendingHours().subtract(leaveRequest.getHours()).max(BigDecimal.ZERO)));
            leaveBalanceRepository.save(balance);
            recordBalanceEvent(balance, leaveRequest.getId(), "REQUEST_REJECTED_RELEASED", LocalDate.now(), null, leaveRequest.getHours(), "Rejected leave released pending balance");
        }
        var before = snapshot(leaveRequest);
        leaveRequest.setStatus(REJECTED);
        leaveRequest.setDecidedByUserId(user.userId());
        leaveRequest.setDecidedAt(Instant.now());
        leaveRequest.setDecisionNote(request == null ? null : request.note());
        leaveRequest.setManagerNote(request == null ? leaveRequest.getManagerNote() : request.note());
        var saved = leaveRequestRepository.save(leaveRequest);
        auditService.record(user, "leave.request.rejected", "LeaveRequest", saved.getId(), before, snapshot(saved), "{\"leaveType\":\"%s\",\"hours\":%s}".formatted(saved.getLeaveType(), saved.getHours()));
        return LeaveRequestDto.from(saved);
    }

    @Transactional
    public LeaveAccrualRunResult runAccruals(LeaveAccrualRunRequest request) {
        var user = currentUserService.requireUser();
        if (!user.hasRole(UserRole.HR_ADMIN) && !user.hasRole(UserRole.SYSTEM_ADMIN)) {
            throw new ForbiddenOperationException("Only HR and system admins can run leave accruals");
        }
        var asOf = request == null || request.asOfDate() == null ? LocalDate.now() : request.asOfDate();
        var period = asOf.with(TemporalAdjusters.firstDayOfMonth());
        var rules = leaveAccrualRuleRepository.findByTenantIdAndActiveTrueAndDeletedFalseOrderByEmploymentTypeAscLeaveTypeAsc(user.tenantId());
        var employees = employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(user.tenantId());
        var updated = 0;
        var total = BigDecimal.ZERO.setScale(2);
        for (var employee : employees) {
            for (var rule : rules) {
                if (!rule.getEmploymentType().equals(employee.getEmploymentType().name())) {
                    continue;
                }
                if (leaveBalanceEventRepository.existsByTenantIdAndEmployeeIdAndLeaveTypeAndEventTypeAndAccrualPeriodAndDeletedFalse(user.tenantId(), employee.getId(), rule.getLeaveType(), "MONTHLY_ACCRUAL", period)) {
                    continue;
                }
                var balance = leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(user.tenantId(), employee.getId(), rule.getLeaveType())
                        .orElseGet(() -> newBalance(employee, rule.getLeaveType(), rule.getMaxBalanceHours()));
                balance.setMaxHours(hours(rule.getMaxBalanceHours()));
                var room = balance.getMaxHours().subtract(balance.getAccruedHours()).max(BigDecimal.ZERO);
                var accrued = rule.getMonthlyAccrualHours().min(room).max(BigDecimal.ZERO);
                balance.setAccruedHours(hours(balance.getAccruedHours().add(accrued)));
                var saved = leaveBalanceRepository.save(balance);
                recordBalanceEvent(saved, null, "MONTHLY_ACCRUAL", asOf, period, accrued, "Monthly demo accrual");
                updated++;
                total = total.add(accrued);
            }
        }
        auditService.record(user, "leave.accrual.run", "LeaveBalance", null, null, "{\"balancesUpdated\":%d,\"hoursAccrued\":%s}".formatted(updated, hours(total)), "{\"period\":\"%s\"}".formatted(period));
        return new LeaveAccrualRunResult(period, updated, hours(total));
    }

    private void requireEmployeeBalanceAccess(AuthenticatedUser user, Employee employee) {
        if (hasManagerAccess(user)) {
            return;
        }
        if (!user.userId().equals(employee.getUserAccountId())) {
            throw new ForbiddenOperationException("You can only view your own leave balances");
        }
    }

    private Employee currentEmployee(AuthenticatedUser user) {
        return employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(user.tenantId(), user.userId())
                .orElseThrow(() -> new ForbiddenOperationException("Current user is not linked to an employee record"));
    }

    private boolean hasManagerAccess(AuthenticatedUser user) {
        return user.hasRole(UserRole.MANAGER) || user.hasRole(UserRole.HR_ADMIN) || user.hasRole(UserRole.SYSTEM_ADMIN);
    }

    private void requireManager(AuthenticatedUser user) {
        if (!hasManagerAccess(user)) {
            throw new ForbiddenOperationException("Manager access is required");
        }
    }

    private LeaveRequest findRequest(UUID id, UUID tenantId) {
        return leaveRequestRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
    }

    private LeaveBalance balanceFor(LeaveRequest request) {
        return leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(request.getTenantId(), request.getEmployeeId(), request.getLeaveType())
                .orElseThrow(() -> new BadRequestException("Leave balance not found for approval workflow"));
    }

    private void requirePending(LeaveRequest request) {
        if (!PENDING.equals(request.getStatus())) {
            throw new BadRequestException("Only pending leave requests can be decided");
        }
    }

    private boolean overlaps(LeaveRequest request, LocalDate from, LocalDate to) {
        var start = from == null ? LocalDate.of(1900, 1, 1) : from;
        var end = to == null ? LocalDate.of(2999, 12, 31) : to;
        return !request.getEndDate().isBefore(start) && !request.getStartDate().isAfter(end);
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("Leave start and end dates are required");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("Leave end date must be on or after start date");
        }
    }

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new BadRequestException("Leave type is required");
        }
        var type = rawType.trim().toUpperCase(Locale.ROOT);
        if (!List.of(VACATION, SICK, UNPAID).contains(type)) {
            throw new BadRequestException("Leave type must be VACATION, SICK, or UNPAID");
        }
        return type;
    }

    private boolean requiresBalance(String leaveType) {
        return VACATION.equals(leaveType) || SICK.equals(leaveType);
    }

    private BigDecimal available(LeaveBalance balance) {
        return hours(balance.getAccruedHours().subtract(balance.getUsedHours()).subtract(balance.getPendingHours()).max(BigDecimal.ZERO));
    }

    private ConflictSummary conflictFor(UUID employeeId, LocalDate start, LocalDate end) {
        var shifts = shiftRepository.findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(currentUserService.tenantId(), start, end).stream()
                .filter(shift -> employeeId.equals(shift.getEmployeeId()))
                .filter(shift -> "ASSIGNED".equals(shift.getStatus()))
                .toList();
        if (shifts.isEmpty()) {
            return new ConflictSummary(0, null);
        }
        var dates = shifts.stream().map(Shift::getShiftDate).distinct().map(LocalDate::toString).toList();
        return new ConflictSummary(shifts.size(), "Conflicts with " + shifts.size() + " scheduled shift(s): " + String.join(", ", dates));
    }

    private LeaveBalance newBalance(Employee employee, String leaveType, BigDecimal maxHours) {
        return LeaveBalance.builder()
                .tenantId(employee.getTenantId())
                .employeeId(employee.getId())
                .employeeName(fullName(employee))
                .leaveType(leaveType)
                .accruedHours(BigDecimal.ZERO.setScale(2))
                .usedHours(BigDecimal.ZERO.setScale(2))
                .pendingHours(BigDecimal.ZERO.setScale(2))
                .maxHours(hours(maxHours))
                .build();
    }

    private void recordBalanceEvent(LeaveBalance balance, UUID requestId, String eventType, LocalDate eventDate, LocalDate accrualPeriod, BigDecimal eventHours, String note) {
        leaveBalanceEventRepository.save(LeaveBalanceEvent.builder()
                .tenantId(balance.getTenantId())
                .leaveBalanceId(balance.getId())
                .employeeId(balance.getEmployeeId())
                .employeeName(balance.getEmployeeName())
                .leaveRequestId(requestId)
                .leaveType(balance.getLeaveType())
                .eventType(eventType)
                .eventDate(eventDate)
                .accrualPeriod(accrualPeriod)
                .hours(hours(eventHours))
                .balanceAfterHours(available(balance))
                .note(note)
                .build());
    }

    private static BigDecimal hours(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String fullName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    private String snapshot(LeaveRequest request) {
        return "{\"employeeName\":\"%s\",\"leaveType\":\"%s\",\"hours\":%s,\"status\":\"%s\",\"conflictCount\":%d}"
                .formatted(request.getEmployeeName(), request.getLeaveType(), request.getHours(), request.getStatus(), request.getConflictCount());
    }

    private record ConflictSummary(int count, String summary) {
    }
}
