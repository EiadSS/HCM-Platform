package com.portfolio.hcm.schedule;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.leave.LeaveRequest;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.org.Department;
import com.portfolio.hcm.org.DepartmentRepository;
import com.portfolio.hcm.org.Location;
import com.portfolio.hcm.org.LocationRepository;
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

import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftRequest;
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
class ScheduleServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 5, 4);

    @Mock
    private ScheduleWeekRepository scheduleWeekRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ScheduleAlertRepository scheduleAlertRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;

    private ScheduleService service;
    private Employee employee;
    private Department department;
    private Location location;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(
                scheduleWeekRepository,
                shiftRepository,
                scheduleAlertRepository,
                employeeRepository,
                departmentRepository,
                locationRepository,
                leaveRequestRepository,
                new ScheduleValidationService(),
                currentUserService,
                auditService
        );
        employee = employee(EMPLOYEE_ID, "Jordan", "Kim");
        department = department(DEPARTMENT_ID);
        location = location(LOCATION_ID);
        when(currentUserService.tenantId()).thenReturn(TENANT_ID);
    }

    @Test
    void publishFailsWhenHighSeverityViolationsExist() {
        var week = week(ScheduleWeekStatus.DRAFT);
        var firstShift = shift(EMPLOYEE_ID, "Jordan Kim", WEEK_START, LocalTime.of(9, 0), LocalTime.of(17, 0), false);
        var overlap = shift(EMPLOYEE_ID, "Jordan Kim", WEEK_START, LocalTime.of(16, 0), LocalTime.of(20, 0), false);
        stubWeek(week, List.of(firstShift, overlap));
        stubReferenceData(List.of(employee), List.of());

        assertThatThrownBy(() -> service.publishWeek(WEEK_START))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Resolve high-severity");

        verify(auditService, never()).record(eq("schedule.published"), eq("ScheduleWeek"), any(), any(), any(), any());
    }

    @Test
    void publishSucceedsWithOnlyMediumWarnings() {
        var week = week(ScheduleWeekStatus.DRAFT);
        var openShift = shift(null, "Open Shift", WEEK_START.plusDays(4), LocalTime.of(16, 0), LocalTime.of(22, 0), false);
        stubWeek(week, List.of(openShift));
        stubReferenceData(List.of(employee), List.of());
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(USER_ID, TENANT_ID, "manager@demo.hcm.local", Set.of(UserRole.MANAGER)));
        when(scheduleWeekRepository.save(any(ScheduleWeek.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleAlertRepository.findByTenantIdAndWeekStartDateAndDeletedFalseOrderByCreatedAtDesc(TENANT_ID, WEEK_START)).thenReturn(List.of());

        var result = service.publishWeek(WEEK_START);

        assertThat(result.week().status()).isEqualTo("PUBLISHED");
        assertThat(openShift.isPublished()).isTrue();
        assertThat(result.validation().violations()).extracting("type").containsExactly("OPEN_SHIFT");
        verify(auditService).record(eq("schedule.published"), eq("ScheduleWeek"), eq(week.getId()), isNull(), contains("\"warnings\":1"), contains("manager-schedule"));
    }

    @Test
    void updateAssignsOpenShiftToEmployee() {
        var week = week(ScheduleWeekStatus.DRAFT);
        var openShift = shift(null, "Open Shift", WEEK_START.plusDays(4), LocalTime.of(16, 0), LocalTime.of(22, 0), false);
        stubWeek(week, List.of(openShift));
        stubReferenceData(List.of(employee), List.of());
        when(shiftRepository.findByIdAndTenantIdAndDeletedFalse(openShift.getId(), TENANT_ID)).thenReturn(Optional.of(openShift));
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(EMPLOYEE_ID, TENANT_ID)).thenReturn(Optional.of(employee));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleAlertRepository.findByTenantIdAndWeekStartDateAndDeletedFalseOrderByCreatedAtDesc(TENANT_ID, WEEK_START)).thenReturn(List.of());

        service.updateShift(WEEK_START, openShift.getId(), new ShiftRequest(
                EMPLOYEE_ID,
                DEPARTMENT_ID,
                LOCATION_ID,
                WEEK_START.plusDays(4),
                LocalTime.of(16, 0),
                LocalTime.of(22, 0)
        ));

        assertThat(openShift.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(openShift.getEmployeeName()).isEqualTo("Jordan Kim");
        assertThat(openShift.getStatus()).isEqualTo("ASSIGNED");
    }

    private void stubWeek(ScheduleWeek week, List<Shift> shifts) {
        when(scheduleWeekRepository.findByTenantIdAndWeekStartDateAndDeletedFalse(TENANT_ID, WEEK_START)).thenReturn(Optional.of(week));
        when(shiftRepository.findByTenantIdAndShiftDateBetweenAndDeletedFalseOrderByShiftDateAscStartTimeAsc(TENANT_ID, WEEK_START, WEEK_START.plusDays(6))).thenReturn(shifts);
    }

    private void stubReferenceData(List<Employee> employees, List<LeaveRequest> leaves) {
        when(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(TENANT_ID)).thenReturn(employees);
        when(departmentRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(department));
        when(locationRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(location));
        when(leaveRequestRepository.findByTenantIdAndDeletedFalseOrderByStartDateAsc(TENANT_ID)).thenReturn(leaves);
    }

    private ScheduleWeek week(ScheduleWeekStatus status) {
        var week = ScheduleWeek.builder()
                .tenantId(TENANT_ID)
                .weekStartDate(WEEK_START)
                .status(status)
                .build();
        week.setId(UUID.randomUUID());
        return week;
    }

    private Shift shift(UUID employeeId, String employeeName, LocalDate date, LocalTime start, LocalTime end, boolean published) {
        var shift = Shift.builder()
                .tenantId(TENANT_ID)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .departmentId(DEPARTMENT_ID)
                .locationId(LOCATION_ID)
                .shiftDate(date)
                .startTime(start)
                .endTime(end)
                .status(employeeId == null ? "OPEN" : "ASSIGNED")
                .published(published)
                .build();
        shift.setId(UUID.randomUUID());
        return shift;
    }

    private Employee employee(UUID id, String firstName, String lastName) {
        var employee = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-100")
                .firstName(firstName)
                .lastName(lastName)
                .workEmail("employee@example.com")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(EmploymentType.PART_TIME)
                .departmentId(DEPARTMENT_ID)
                .locationId(LOCATION_ID)
                .hourlyRate(new BigDecimal("24.00"))
                .weeklyHourCap(new BigDecimal("28"))
                .hireDate(LocalDate.of(2024, 1, 1))
                .build();
        employee.setId(id);
        return employee;
    }

    private Department department(UUID id) {
        var department = Department.builder()
                .tenantId(TENANT_ID)
                .name("Retail")
                .costCenter("RET-200")
                .build();
        department.setId(id);
        return department;
    }

    private Location location(UUID id) {
        var location = Location.builder()
                .tenantId(TENANT_ID)
                .name("Downtown Store")
                .timezone("America/Toronto")
                .region("Ontario")
                .build();
        location.setId(id);
        return location;
    }
}
