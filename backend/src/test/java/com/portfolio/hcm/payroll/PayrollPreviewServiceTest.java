package com.portfolio.hcm.payroll;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.integration.WebhookEventService;
import com.portfolio.hcm.leave.LeaveRequest;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.org.Location;
import com.portfolio.hcm.org.LocationRepository;
import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.time.TimeBreak;
import com.portfolio.hcm.time.TimeBreakRepository;
import com.portfolio.hcm.time.TimeBreakSource;
import com.portfolio.hcm.time.TimeEntry;
import com.portfolio.hcm.time.TimeEntryRepository;
import com.portfolio.hcm.time.TimeEntrySource;
import com.portfolio.hcm.time.TimeEntryStatus;
import com.portfolio.hcm.time.Timesheet;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
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

import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollPreviewServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 27);

    @Mock
    private PayrollPreviewRepository payrollPreviewRepository;
    @Mock
    private PayrollPreviewLineRepository payrollPreviewLineRepository;
    @Mock
    private PayRuleConfigRepository payRuleConfigRepository;
    @Mock
    private PayrollHolidayRepository payrollHolidayRepository;
    @Mock
    private TimesheetRepository timesheetRepository;
    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private TimeBreakRepository timeBreakRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;
    @Mock
    private WebhookEventService webhookEventService;

    private PayrollPreviewService service;

    @BeforeEach
    void setUp() {
        service = new PayrollPreviewService(
                payrollPreviewRepository,
                payrollPreviewLineRepository,
                payRuleConfigRepository,
                payrollHolidayRepository,
                timesheetRepository,
                timeEntryRepository,
                timeBreakRepository,
                employeeRepository,
                locationRepository,
                leaveRequestRepository,
                new PayrollPreviewCalculator(),
                currentUserService,
                auditService,
                webhookEventService
        );
    }

    @Test
    void generatesPreviewFromApprovedAndSubmittedTimeDataWithExplanationLines() {
        var employee = employee();
        var location = Location.builder().tenantId(TENANT_ID).name("Downtown Store").timezone("America/Toronto").region("Ontario").build();
        location.setId(LOCATION_ID);
        var timesheet = timesheet(TimesheetStatus.SUBMITTED);
        var entries = List.of(
                entry(timesheet, 0),
                entry(timesheet, 1),
                entry(timesheet, 2),
                entry(timesheet, 3),
                entry(timesheet, 4)
        );
        var breakRow = breakRow(entries.get(3), 60);

        stubUser();
        when(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(TENANT_ID)).thenReturn(List.of(employee));
        when(locationRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(location));
        when(timesheetRepository.findByTenantIdAndWeekStartDateBetweenAndStatusInAndDeletedFalseOrderByWeekStartDateAscEmployeeNameAsc(TENANT_ID, WEEK_START, WEEK_START.plusDays(6), List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED))).thenReturn(List.of(timesheet));
        when(timeEntryRepository.findByTimesheetIdInAndDeletedFalseOrderByClockInAtAsc(List.of(timesheet.getId()))).thenReturn(entries);
        when(timeBreakRepository.findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(entries.stream().map(TimeEntry::getId).toList())).thenReturn(List.of(breakRow));
        when(payRuleConfigRepository.findByTenantIdAndDeletedFalseOrderByEffectiveStartDateDesc(TENANT_ID)).thenReturn(List.of(rule()));
        when(payrollHolidayRepository.findByTenantIdAndHolidayDateBetweenAndDeletedFalseOrderByHolidayDateAsc(TENANT_ID, WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(holiday()));
        when(leaveRequestRepository.findByTenantIdAndStatusAndLeaveTypeAndEndDateGreaterThanEqualAndStartDateLessThanEqualAndDeletedFalse(TENANT_ID, "APPROVED", "UNPAID", WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(leave()));
        when(payrollPreviewRepository.save(any(PayrollPreview.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollPreviewLineRepository.save(any(PayrollPreviewLine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generate(new PayrollPreviewRequest(WEEK_START, WEEK_START.plusDays(6), null));

        assertThat(result.preview().regularHours()).isEqualByComparingTo("40.00");
        assertThat(result.preview().overtimeHours()).isEqualByComparingTo("4.00");
        assertThat(result.preview().unpaidBreakHours()).isEqualByComparingTo("1.00");
        assertThat(result.preview().unpaidLeaveHours()).isEqualByComparingTo("4.00");
        assertThat(result.preview().holidayHours()).isEqualByComparingTo("9.00");
        assertThat(result.preview().grossPay()).isEqualByComparingTo("1174.13");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).explanation())
                .contains("40 regular hours")
                .contains("4 overtime hours")
                .contains("1 unpaid break")
                .contains("9 holiday worked hours")
                .contains("4 approved unpaid leave");
        verify(auditService).record(any(AuthenticatedUser.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void blocksPreviewWhenSubmittedTimesheetHasMissedPunch() {
        var employee = employee();
        var timesheet = timesheet(TimesheetStatus.SUBMITTED);
        var openEntry = entry(timesheet, 0);
        openEntry.setClockOutAt(null);
        openEntry.setStatus(TimeEntryStatus.MISSED_PUNCH);

        stubUser();
        when(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(TENANT_ID)).thenReturn(List.of(employee));
        when(locationRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of());
        when(timesheetRepository.findByTenantIdAndWeekStartDateBetweenAndStatusInAndDeletedFalseOrderByWeekStartDateAscEmployeeNameAsc(TENANT_ID, WEEK_START, WEEK_START.plusDays(6), List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED))).thenReturn(List.of(timesheet));
        when(timeEntryRepository.findByTimesheetIdInAndDeletedFalseOrderByClockInAtAsc(List.of(timesheet.getId()))).thenReturn(List.of(openEntry));
        when(timeBreakRepository.findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(List.of(openEntry.getId()))).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new PayrollPreviewRequest(WEEK_START, WEEK_START.plusDays(6), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missed punch");
    }

    private void stubUser() {
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(USER_ID, TENANT_ID, "payroll@example.com", Set.of(UserRole.PAYROLL_ADMIN)));
    }

    private Employee employee() {
        var employee = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-007")
                .firstName("Amara")
                .lastName("Singh")
                .workEmail("amara@example.com")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(EmploymentType.PART_TIME)
                .locationId(LOCATION_ID)
                .hourlyRate(new BigDecimal("23.25"))
                .weeklyHourCap(new BigDecimal("28.00"))
                .hireDate(LocalDate.of(2024, 1, 8))
                .build();
        employee.setId(EMPLOYEE_ID);
        return employee;
    }

    private Timesheet timesheet(TimesheetStatus status) {
        var timesheet = Timesheet.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Amara Singh")
                .weekStartDate(WEEK_START)
                .regularHours(BigDecimal.ZERO.setScale(2))
                .overtimeHours(BigDecimal.ZERO.setScale(2))
                .status(status)
                .lockedPayPeriod(false)
                .build();
        timesheet.setId(UUID.randomUUID());
        return timesheet;
    }

    private TimeEntry entry(Timesheet timesheet, int dayOffset) {
        var start = WEEK_START.plusDays(dayOffset).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC);
        var end = WEEK_START.plusDays(dayOffset).atTime(18, 0).toInstant(java.time.ZoneOffset.UTC);
        var entry = TimeEntry.builder()
                .tenantId(TENANT_ID)
                .timesheetId(timesheet.getId())
                .employeeId(EMPLOYEE_ID)
                .employeeName("Amara Singh")
                .entryDate(WEEK_START.plusDays(dayOffset))
                .clockInAt(start)
                .clockOutAt(end)
                .source(TimeEntrySource.CLOCK)
                .status(TimeEntryStatus.COMPLETE)
                .build();
        entry.setId(UUID.randomUUID());
        return entry;
    }

    private TimeBreak breakRow(TimeEntry entry, int minutes) {
        var timeBreak = TimeBreak.builder()
                .tenantId(TENANT_ID)
                .timeEntryId(entry.getId())
                .breakStartAt(entry.getClockInAt().plusSeconds(4 * 3_600L))
                .breakEndAt(entry.getClockInAt().plusSeconds(4 * 3_600L + minutes * 60L))
                .durationMinutes(minutes)
                .source(TimeBreakSource.CLOCK)
                .build();
        timeBreak.setId(UUID.randomUUID());
        return timeBreak;
    }

    private PayRuleConfig rule() {
        var rule = PayRuleConfig.builder()
                .tenantId(TENANT_ID)
                .locationId(LOCATION_ID)
                .name("Downtown Store holiday premium rule")
                .effectiveStartDate(LocalDate.of(2020, 1, 1))
                .weeklyRegularHours(new BigDecimal("40.00"))
                .overtimeMultiplier(new BigDecimal("1.50"))
                .holidayPremiumMultiplier(new BigDecimal("1.50"))
                .unpaidBreaksDeductible(true)
                .build();
        rule.setId(UUID.randomUUID());
        return rule;
    }

    private PayrollHoliday holiday() {
        var holiday = PayrollHoliday.builder()
                .tenantId(TENANT_ID)
                .locationId(LOCATION_ID)
                .holidayDate(WEEK_START.plusDays(2))
                .name("Retail Heritage Day")
                .build();
        holiday.setId(UUID.randomUUID());
        return holiday;
    }

    private LeaveRequest leave() {
        var leave = LeaveRequest.builder()
                .tenantId(TENANT_ID)
                .employeeId(EMPLOYEE_ID)
                .employeeName("Amara Singh")
                .leaveType("UNPAID")
                .startDate(WEEK_START.plusDays(5))
                .endDate(WEEK_START.plusDays(5))
                .hours(new BigDecimal("4.00"))
                .status("APPROVED")
                .build();
        leave.setId(UUID.randomUUID());
        return leave;
    }
}
