package com.portfolio.hcm.leave;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.schedule.Shift;
import com.portfolio.hcm.schedule.ShiftRepository;
import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveDecisionRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestCreate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_USER_ID = UUID.randomUUID();
    private static final UUID MANAGER_USER_ID = UUID.randomUUID();
    private static final UUID HR_USER_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 5, 12);

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private LeaveAccrualRuleRepository leaveAccrualRuleRepository;
    @Mock
    private LeaveBalanceEventRepository leaveBalanceEventRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;

    private LeaveService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new LeaveService(
                leaveRequestRepository,
                leaveBalanceRepository,
                leaveAccrualRuleRepository,
                leaveBalanceEventRepository,
                employeeRepository,
                shiftRepository,
                currentUserService,
                auditService
        );
        employee = employee(EMPLOYEE_ID, EMPLOYEE_USER_ID, EmploymentType.FULL_TIME);
    }

    @Test
    void vacationRequestReservesPendingBalanceAndWarnsForScheduleConflict() {
        var user = employeeUser();
        var balance = balance("VACATION", "40.00", "8.00", "4.00", "80.00");
        stubEmployeeUser(user);
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(TENANT_ID, EMPLOYEE_ID, "VACATION")).thenReturn(Optional.of(balance));
        when(shiftRepository.findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(TENANT_ID, START, START.plusDays(1)))
                .thenReturn(List.of(assignedShift(START), assignedShift(START.plusDays(1))));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveBalanceEventRepository.save(any(LeaveBalanceEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(new LeaveRequestCreate("vacation", START, START.plusDays(1), new BigDecimal("8.00"), "Family trip"));

        assertThat(dto.leaveType()).isEqualTo("VACATION");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.conflict()).isTrue();
        assertThat(dto.conflictCount()).isEqualTo(2);
        assertThat(dto.conflictSummary()).contains("scheduled shift");
        assertThat(balance.getPendingHours()).isEqualByComparingTo("12.00");
        verify(leaveBalanceEventRepository).save(any(LeaveBalanceEvent.class));
        verify(auditService).record(eq(user), eq("leave.request.submitted"), eq("LeaveRequest"), eq(dto.id()), isNull(), contains("\"status\":\"PENDING\""), contains("\"conflicts\":2"));
    }

    @Test
    void paidRequestFailsWhenItExceedsAvailableBalance() {
        var user = employeeUser();
        when(currentUserService.requireUser()).thenReturn(user);
        when(employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(TENANT_ID, user.userId())).thenReturn(Optional.of(employee));
        var balance = balance("SICK", "6.00", "2.00", "2.00", "40.00");
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(TENANT_ID, EMPLOYEE_ID, "SICK")).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.create(new LeaveRequestCreate("sick", START, START, new BigDecimal("8.00"), "Illness")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient sick balance");

        verify(leaveRequestRepository, never()).save(any());
        verify(leaveBalanceRepository, never()).save(any());
    }

    @Test
    void unpaidLeaveSkipsBalanceValidationAndReservation() {
        var user = employeeUser();
        stubEmployeeUser(user);
        when(shiftRepository.findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(TENANT_ID, START, START)).thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(new LeaveRequestCreate("unpaid", START, START, new BigDecimal("4.00"), "Appointment"));

        assertThat(dto.leaveType()).isEqualTo("UNPAID");
        assertThat(dto.hours()).isEqualByComparingTo("4.00");
        verify(leaveBalanceRepository, never()).findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(any(), any(), any());
        verify(leaveBalanceRepository, never()).save(any());
    }

    @Test
    void approvalMovesPendingBalanceToUsedAndWritesAudit() {
        var user = managerUser();
        var request = leaveRequest("VACATION", "PENDING", "8.00");
        var balance = balance("VACATION", "40.00", "4.00", "8.00", "80.00");
        when(currentUserService.requireUser()).thenReturn(user);
        when(leaveRequestRepository.findByIdAndTenantIdAndDeletedFalse(request.getId(), TENANT_ID)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(TENANT_ID, EMPLOYEE_ID, "VACATION")).thenReturn(Optional.of(balance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveBalanceEventRepository.save(any(LeaveBalanceEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.approve(request.getId(), new LeaveDecisionRequest("Coverage arranged"));

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.decisionNote()).isEqualTo("Coverage arranged");
        assertThat(balance.getPendingHours()).isEqualByComparingTo("0.00");
        assertThat(balance.getUsedHours()).isEqualByComparingTo("12.00");
        verify(auditService).record(eq(user), eq("leave.request.approved"), eq("LeaveRequest"), eq(request.getId()), contains("\"status\":\"PENDING\""), contains("\"status\":\"APPROVED\""), contains("\"hours\":8.00"));
    }

    @Test
    void rejectionReleasesPendingBalanceAndWritesAudit() {
        var user = managerUser();
        var request = leaveRequest("SICK", "PENDING", "6.00");
        var balance = balance("SICK", "20.00", "2.00", "6.00", "40.00");
        when(currentUserService.requireUser()).thenReturn(user);
        when(leaveRequestRepository.findByIdAndTenantIdAndDeletedFalse(request.getId(), TENANT_ID)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(TENANT_ID, EMPLOYEE_ID, "SICK")).thenReturn(Optional.of(balance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveBalanceEventRepository.save(any(LeaveBalanceEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.reject(request.getId(), new LeaveDecisionRequest("Try another day"));

        assertThat(dto.status()).isEqualTo("REJECTED");
        assertThat(balance.getPendingHours()).isEqualByComparingTo("0.00");
        assertThat(balance.getUsedHours()).isEqualByComparingTo("2.00");
        verify(auditService).record(eq(user), eq("leave.request.rejected"), eq("LeaveRequest"), eq(request.getId()), contains("\"status\":\"PENDING\""), contains("\"status\":\"REJECTED\""), contains("\"hours\":6.00"));
    }

    @Test
    void monthlyAccrualAppliesOncePerEmployeeTypeAndCapsAtMaxBalance() {
        var user = hrUser();
        var rule = LeaveAccrualRule.builder()
                .tenantId(TENANT_ID)
                .employmentType("FULL_TIME")
                .leaveType("VACATION")
                .monthlyAccrualHours(new BigDecimal("10.00"))
                .maxBalanceHours(new BigDecimal("30.00"))
                .active(true)
                .build();
        var balance = balance("VACATION", "25.00", "0.00", "0.00", "30.00");
        when(currentUserService.requireUser()).thenReturn(user);
        when(leaveAccrualRuleRepository.findByTenantIdAndActiveTrueAndDeletedFalseOrderByEmploymentTypeAscLeaveTypeAsc(TENANT_ID)).thenReturn(List.of(rule));
        when(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(TENANT_ID)).thenReturn(List.of(employee));
        when(leaveBalanceEventRepository.existsByTenantIdAndEmployeeIdAndLeaveTypeAndEventTypeAndAccrualPeriodAndDeletedFalse(
                TENANT_ID,
                EMPLOYEE_ID,
                "VACATION",
                "MONTHLY_ACCRUAL",
                LocalDate.of(2026, 5, 1)
        )).thenReturn(false);
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeAndDeletedFalse(TENANT_ID, EMPLOYEE_ID, "VACATION")).thenReturn(Optional.of(balance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveBalanceEventRepository.save(any(LeaveBalanceEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.runAccruals(new LeaveAccrualRunRequest(LocalDate.of(2026, 5, 20)));

        assertThat(result.accrualPeriod()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.balancesUpdated()).isEqualTo(1);
        assertThat(result.hoursAccrued()).isEqualByComparingTo("5.00");
        assertThat(balance.getAccruedHours()).isEqualByComparingTo("30.00");
        verify(auditService).record(eq(user), eq("leave.accrual.run"), eq("LeaveBalance"), isNull(), isNull(), contains("\"balancesUpdated\":1"), contains("\"period\":\"2026-05-01\""));
    }

    @Test
    void nonHrUserCannotRunAccruals() {
        when(currentUserService.requireUser()).thenReturn(employeeUser());

        assertThatThrownBy(() -> service.runAccruals(new LeaveAccrualRunRequest(LocalDate.of(2026, 5, 20))))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only HR and system admins");
    }

    private void stubEmployeeUser(AuthenticatedUser user) {
        when(currentUserService.requireUser()).thenReturn(user);
        when(currentUserService.tenantId()).thenReturn(TENANT_ID);
        when(employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(TENANT_ID, user.userId())).thenReturn(Optional.of(employee));
    }

    private AuthenticatedUser employeeUser() {
        return new AuthenticatedUser(EMPLOYEE_USER_ID, TENANT_ID, "employee@demo.hcm.local", Set.of(UserRole.EMPLOYEE));
    }

    private AuthenticatedUser managerUser() {
        return new AuthenticatedUser(MANAGER_USER_ID, TENANT_ID, "manager@demo.hcm.local", Set.of(UserRole.MANAGER));
    }

    private AuthenticatedUser hrUser() {
        return new AuthenticatedUser(HR_USER_ID, TENANT_ID, "hr@demo.hcm.local", Set.of(UserRole.HR_ADMIN));
    }

    private Employee employee(UUID employeeId, UUID userId, EmploymentType employmentType) {
        var value = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-100")
                .firstName("Jordan")
                .lastName("Kim")
                .workEmail("employee@demo.hcm.local")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(employmentType)
                .userAccountId(userId)
                .hourlyRate(new BigDecimal("24.75"))
                .weeklyHourCap(new BigDecimal("28.00"))
                .hireDate(LocalDate.of(2024, 1, 1))
                .build();
        value.setId(employeeId);
        return value;
    }

    private LeaveBalance balance(String leaveType, String accrued, String used, String pending, String max) {
        var balance = LeaveBalance.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Jordan Kim")
                .leaveType(leaveType)
                .accruedHours(new BigDecimal(accrued))
                .usedHours(new BigDecimal(used))
                .pendingHours(new BigDecimal(pending))
                .maxHours(new BigDecimal(max))
                .build();
        balance.setId(UUID.randomUUID());
        return balance;
    }

    private LeaveRequest leaveRequest(String leaveType, String status, String hours) {
        var request = LeaveRequest.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Jordan Kim")
                .requestedByUserId(EMPLOYEE_USER_ID)
                .leaveType(leaveType)
                .startDate(START)
                .endDate(START)
                .hours(new BigDecimal(hours))
                .status(status)
                .conflictCount(0)
                .build();
        request.setId(UUID.randomUUID());
        return request;
    }

    private Shift assignedShift(LocalDate date) {
        var shift = Shift.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Jordan Kim")
                .shiftDate(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .status("ASSIGNED")
                .published(true)
                .build();
        shift.setId(UUID.randomUUID());
        return shift;
    }
}
