package com.portfolio.hcm.time;

import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.integration.WebhookEventService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();

    @Mock
    private TimesheetRepository timesheetRepository;
    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private TimeBreakRepository timeBreakRepository;
    @Mock
    private TimesheetChangeRequestRepository changeRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;
    @Mock
    private WebhookEventService webhookEventService;

    private TimesheetService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new TimesheetService(
                timesheetRepository,
                timeEntryRepository,
                timeBreakRepository,
                changeRequestRepository,
                employeeRepository,
                shiftRepository,
                auditLogRepository,
                new TimesheetValidationService(),
                currentUserService,
                auditService,
                webhookEventService
        );
        employee = employee();
    }

    @Test
    void recalculatesRegularAndOvertimeFromEntriesAndBreaks() {
        var timesheet = timesheet(TimesheetStatus.DRAFT, false);
        var first = entry(timesheet, Instant.parse("2026-05-04T13:00:00Z"), Instant.parse("2026-05-05T19:00:00Z"));
        var second = entry(timesheet, Instant.parse("2026-05-06T13:00:00Z"), Instant.parse("2026-05-07T04:00:00Z"));
        when(timeEntryRepository.findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(timesheet.getId())).thenReturn(List.of(first, second));
        when(timeBreakRepository.findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(first.getId())).thenReturn(List.of(breakRow(first, 60)));
        when(timeBreakRepository.findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(second.getId())).thenReturn(List.of());
        when(timesheetRepository.save(any(Timesheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recalculate(timesheet);

        assertThat(timesheet.getRegularHours()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(timesheet.getOvertimeHours()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void submitBlocksHighSeverityValidationIssues() {
        var timesheet = timesheet(TimesheetStatus.DRAFT, false);
        var openEntry = entry(timesheet, Instant.parse("2026-05-04T13:00:00Z"), null);
        stubTenant();
        stubEmployeeUser();
        when(timesheetRepository.findByIdAndTenantIdAndDeletedFalse(timesheet.getId(), TENANT_ID)).thenReturn(Optional.of(timesheet));
        when(timeEntryRepository.findByTimesheetIdAndDeletedFalseOrderByClockInAtAsc(timesheet.getId())).thenReturn(List.of(openEntry));
        when(timeBreakRepository.findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(List.of(openEntry.getId()))).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(timesheet.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missed punch");
    }

    @Test
    void approvedTimesheetRequiresApprovedChangeRequestBeforeEditing() {
        var timesheet = timesheet(TimesheetStatus.APPROVED, false);
        stubTenant();
        stubEmployeeUser();
        when(timesheetRepository.findByIdAndTenantIdAndDeletedFalse(timesheet.getId(), TENANT_ID)).thenReturn(Optional.of(timesheet));

        assertThatThrownBy(() -> service.addEntry(timesheet.getId(), new TimesheetDtos.ManualTimeEntryRequest(
                Instant.parse("2026-05-04T13:00:00Z"),
                Instant.parse("2026-05-04T21:00:00Z"),
                null,
                null,
                "Correction"
        ))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("draft, rejected, or change-requested");
    }

    @Test
    void changeRequestApprovalReopensApprovedTimesheet() {
        var timesheet = timesheet(TimesheetStatus.APPROVED, false);
        var request = TimesheetChangeRequest.builder()
                .tenantId(TENANT_ID)
                .timesheetId(timesheet.getId())
                .requestedByUserId(USER_ID)
                .requesterEmail("employee@example.com")
                .reason("Fix missed punch")
                .status(TimesheetChangeRequestStatus.PENDING)
                .build();
        request.setId(UUID.randomUUID());
        stubTenant();
        when(timesheetRepository.findByIdAndTenantIdAndDeletedFalse(timesheet.getId(), TENANT_ID)).thenReturn(Optional.of(timesheet));
        when(changeRequestRepository.findByIdAndTenantIdAndDeletedFalse(request.getId(), TENANT_ID)).thenReturn(Optional.of(request));
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(USER_ID, TENANT_ID, "manager@example.com", Set.of(UserRole.MANAGER)));

        var result = service.approveChangeRequest(timesheet.getId(), request.getId(), new TimesheetDtos.DecisionRequest("Reopen it"));

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(timesheet.getStatus()).isEqualTo(TimesheetStatus.CHANGE_REQUESTED);
    }

    @Test
    void lockRequiresApprovedTimesheet() {
        var draft = timesheet(TimesheetStatus.DRAFT, false);
        stubTenant();
        when(timesheetRepository.findByIdAndTenantIdAndDeletedFalse(draft.getId(), TENANT_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.lock(draft.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only approved");
    }

    private void stubEmployeeUser() {
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(USER_ID, TENANT_ID, "employee@example.com", Set.of(UserRole.EMPLOYEE)));
        when(employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(TENANT_ID, USER_ID)).thenReturn(Optional.of(employee));
    }

    private void stubTenant() {
        when(currentUserService.tenantId()).thenReturn(TENANT_ID);
    }

    private Timesheet timesheet(TimesheetStatus status, boolean locked) {
        var timesheet = Timesheet.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Jordan Kim")
                .weekStartDate(LocalDate.of(2026, 5, 4))
                .regularHours(BigDecimal.ZERO.setScale(2))
                .overtimeHours(BigDecimal.ZERO.setScale(2))
                .status(status)
                .lockedPayPeriod(locked)
                .build();
        timesheet.setId(UUID.randomUUID());
        return timesheet;
    }

    private TimeEntry entry(Timesheet timesheet, Instant in, Instant out) {
        var entry = TimeEntry.builder()
                .tenantId(TENANT_ID)
                .timesheetId(timesheet.getId())
                .employeeId(EMPLOYEE_ID)
                .employeeName("Jordan Kim")
                .entryDate(LocalDate.of(2026, 5, 4))
                .clockInAt(in)
                .clockOutAt(out)
                .source(TimeEntrySource.CLOCK)
                .status(out == null ? TimeEntryStatus.OPEN : TimeEntryStatus.COMPLETE)
                .build();
        entry.setId(UUID.randomUUID());
        return entry;
    }

    private TimeBreak breakRow(TimeEntry entry, int minutes) {
        var timeBreak = TimeBreak.builder()
                .tenantId(TENANT_ID)
                .timeEntryId(entry.getId())
                .breakStartAt(entry.getClockInAt().plusSeconds(3600))
                .breakEndAt(entry.getClockInAt().plusSeconds(3600 + minutes * 60L))
                .durationMinutes(minutes)
                .source(TimeBreakSource.CLOCK)
                .build();
        timeBreak.setId(UUID.randomUUID());
        return timeBreak;
    }

    private Employee employee() {
        var employee = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-004")
                .firstName("Jordan")
                .lastName("Kim")
                .workEmail("employee@example.com")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(EmploymentType.PART_TIME)
                .hourlyRate(new BigDecimal("24.00"))
                .weeklyHourCap(new BigDecimal("28"))
                .hireDate(LocalDate.of(2024, 1, 1))
                .build();
        employee.setId(EMPLOYEE_ID);
        return employee;
    }
}
