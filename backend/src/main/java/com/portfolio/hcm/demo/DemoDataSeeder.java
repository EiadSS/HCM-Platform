package com.portfolio.hcm.demo;

import com.portfolio.hcm.audit.AuditLog;
import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.integration.ImportJob;
import com.portfolio.hcm.integration.ImportJobRepository;
import com.portfolio.hcm.integration.EmployeeImportRow;
import com.portfolio.hcm.integration.EmployeeImportRowRepository;
import com.portfolio.hcm.integration.WebhookDeliveryAttempt;
import com.portfolio.hcm.integration.WebhookDeliveryAttemptRepository;
import com.portfolio.hcm.integration.WebhookEvent;
import com.portfolio.hcm.integration.WebhookEventRepository;
import com.portfolio.hcm.leave.LeaveAccrualRule;
import com.portfolio.hcm.leave.LeaveAccrualRuleRepository;
import com.portfolio.hcm.leave.LeaveBalance;
import com.portfolio.hcm.leave.LeaveBalanceEvent;
import com.portfolio.hcm.leave.LeaveBalanceEventRepository;
import com.portfolio.hcm.leave.LeaveBalanceRepository;
import com.portfolio.hcm.leave.LeaveRequest;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.org.Department;
import com.portfolio.hcm.org.DepartmentRepository;
import com.portfolio.hcm.org.JobTitle;
import com.portfolio.hcm.org.JobTitleRepository;
import com.portfolio.hcm.org.Location;
import com.portfolio.hcm.org.LocationRepository;
import com.portfolio.hcm.payroll.PayRuleConfig;
import com.portfolio.hcm.payroll.PayRuleConfigRepository;
import com.portfolio.hcm.payroll.PayrollHoliday;
import com.portfolio.hcm.payroll.PayrollHolidayRepository;
import com.portfolio.hcm.payroll.PayrollPreview;
import com.portfolio.hcm.payroll.PayrollPreviewLine;
import com.portfolio.hcm.payroll.PayrollPreviewLineRepository;
import com.portfolio.hcm.payroll.PayrollPreviewRepository;
import com.portfolio.hcm.schedule.ScheduleAlert;
import com.portfolio.hcm.schedule.ScheduleAlertRepository;
import com.portfolio.hcm.schedule.ScheduleWeek;
import com.portfolio.hcm.schedule.ScheduleWeekRepository;
import com.portfolio.hcm.schedule.ScheduleWeekStatus;
import com.portfolio.hcm.schedule.Shift;
import com.portfolio.hcm.schedule.ShiftRepository;
import com.portfolio.hcm.tenant.Tenant;
import com.portfolio.hcm.tenant.TenantRepository;
import com.portfolio.hcm.time.Timesheet;
import com.portfolio.hcm.time.TimeBreak;
import com.portfolio.hcm.time.TimeBreakRepository;
import com.portfolio.hcm.time.TimeBreakSource;
import com.portfolio.hcm.time.TimeEntry;
import com.portfolio.hcm.time.TimeEntryRepository;
import com.portfolio.hcm.time.TimeEntrySource;
import com.portfolio.hcm.time.TimeEntryStatus;
import com.portfolio.hcm.time.TimesheetChangeRequest;
import com.portfolio.hcm.time.TimesheetChangeRequestRepository;
import com.portfolio.hcm.time.TimesheetChangeRequestStatus;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
import com.portfolio.hcm.user.AccountStatus;
import com.portfolio.hcm.user.UserAccount;
import com.portfolio.hcm.user.UserAccountRepository;
import com.portfolio.hcm.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "DemoPass123!";

    private final boolean seedDemo;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final JobTitleRepository jobTitleRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleAlertRepository scheduleAlertRepository;
    private final ScheduleWeekRepository scheduleWeekRepository;
    private final ShiftRepository shiftRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeBreakRepository timeBreakRepository;
    private final TimesheetChangeRequestRepository changeRequestRepository;
    private final PayrollPreviewRepository payrollPreviewRepository;
    private final PayrollPreviewLineRepository payrollPreviewLineRepository;
    private final PayRuleConfigRepository payRuleConfigRepository;
    private final PayrollHolidayRepository payrollHolidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveAccrualRuleRepository leaveAccrualRuleRepository;
    private final LeaveBalanceEventRepository leaveBalanceEventRepository;
    private final ImportJobRepository importJobRepository;
    private final EmployeeImportRowRepository employeeImportRowRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDeliveryAttemptRepository webhookDeliveryAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            @Value("${app.demo.seed}") boolean seedDemo,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            JobTitleRepository jobTitleRepository,
            EmployeeRepository employeeRepository,
            ScheduleAlertRepository scheduleAlertRepository,
            ScheduleWeekRepository scheduleWeekRepository,
            ShiftRepository shiftRepository,
            TimesheetRepository timesheetRepository,
            TimeEntryRepository timeEntryRepository,
            TimeBreakRepository timeBreakRepository,
            TimesheetChangeRequestRepository changeRequestRepository,
            PayrollPreviewRepository payrollPreviewRepository,
            PayrollPreviewLineRepository payrollPreviewLineRepository,
            PayRuleConfigRepository payRuleConfigRepository,
            PayrollHolidayRepository payrollHolidayRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveAccrualRuleRepository leaveAccrualRuleRepository,
            LeaveBalanceEventRepository leaveBalanceEventRepository,
            ImportJobRepository importJobRepository,
            EmployeeImportRowRepository employeeImportRowRepository,
            WebhookEventRepository webhookEventRepository,
            WebhookDeliveryAttemptRepository webhookDeliveryAttemptRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.seedDemo = seedDemo;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.employeeRepository = employeeRepository;
        this.scheduleAlertRepository = scheduleAlertRepository;
        this.scheduleWeekRepository = scheduleWeekRepository;
        this.shiftRepository = shiftRepository;
        this.timesheetRepository = timesheetRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.timeBreakRepository = timeBreakRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.payrollPreviewRepository = payrollPreviewRepository;
        this.payrollPreviewLineRepository = payrollPreviewLineRepository;
        this.payRuleConfigRepository = payRuleConfigRepository;
        this.payrollHolidayRepository = payrollHolidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveAccrualRuleRepository = leaveAccrualRuleRepository;
        this.leaveBalanceEventRepository = leaveBalanceEventRepository;
        this.importJobRepository = importJobRepository;
        this.employeeImportRowRepository = employeeImportRowRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookDeliveryAttemptRepository = webhookDeliveryAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedDemo) {
            seedIfMissing();
        }
    }

    @Transactional
    public void seedIfMissing() {
        if (tenantRepository.findBySlug("northstar-retail").isEmpty()) {
            seedDemoTenant();
        }
    }

    @Transactional
    public void resetDemoTenant() {
        tenantRepository.findBySlug("northstar-retail").ifPresent(tenant -> {
            var tenantId = tenant.getId();
            webhookDeliveryAttemptRepository.deleteByTenantId(tenantId);
            webhookEventRepository.deleteByTenantId(tenantId);
            employeeImportRowRepository.deleteByTenantId(tenantId);
            importJobRepository.deleteByTenantId(tenantId);
            leaveBalanceEventRepository.deleteByTenantId(tenantId);
            leaveBalanceRepository.deleteByTenantId(tenantId);
            leaveAccrualRuleRepository.deleteByTenantId(tenantId);
            leaveRequestRepository.deleteByTenantId(tenantId);
            payrollPreviewLineRepository.deleteByTenantId(tenantId);
            payrollPreviewRepository.deleteByTenantId(tenantId);
            payrollHolidayRepository.deleteByTenantId(tenantId);
            payRuleConfigRepository.deleteByTenantId(tenantId);
            timeBreakRepository.deleteByTenantId(tenantId);
            timeEntryRepository.deleteByTenantId(tenantId);
            changeRequestRepository.deleteByTenantId(tenantId);
            timesheetRepository.deleteByTenantId(tenantId);
            scheduleAlertRepository.deleteByTenantId(tenantId);
            shiftRepository.deleteByTenantId(tenantId);
            scheduleWeekRepository.deleteByTenantId(tenantId);
            auditLogRepository.deleteByTenantId(tenantId);
            employeeRepository.deleteByTenantId(tenantId);
            departmentRepository.deleteByTenantId(tenantId);
            locationRepository.deleteByTenantId(tenantId);
            jobTitleRepository.deleteByTenantId(tenantId);
            userAccountRepository.deleteByTenantId(tenantId);
            tenantRepository.delete(tenant);
        });
        seedDemoTenant();
    }

    private void seedDemoTenant() {
        var tenant = Tenant.builder()
                .slug("northstar-retail")
                .name("Northstar Retail Group")
                .status("ACTIVE")
                .demoMode(true)
                .build();
        tenant.setId(DemoIds.TENANT);
        tenantRepository.save(tenant);

        var admin = user(DemoIds.ADMIN_USER, "admin@demo.hcm.local", "Alex Rivera", UserRole.SYSTEM_ADMIN);
        var hr = user(DemoIds.HR_USER, "hr@demo.hcm.local", "Priya Shah", UserRole.HR_ADMIN);
        var manager = user(DemoIds.MANAGER_USER, "manager@demo.hcm.local", "Maya Thompson", UserRole.MANAGER);
        var employeeUser = user(DemoIds.EMPLOYEE_USER, "employee@demo.hcm.local", "Jordan Kim", UserRole.EMPLOYEE);
        var payroll = user(DemoIds.PAYROLL_USER, "payroll@demo.hcm.local", "Sam Carter", UserRole.PAYROLL_ADMIN);
        userAccountRepository.saveAll(java.util.List.of(admin, hr, manager, employeeUser, payroll));

        departmentRepository.saveAll(java.util.List.of(
                department(DemoIds.DEPT_OPERATIONS, "Operations", "OPS-100"),
                department(DemoIds.DEPT_RETAIL, "Retail", "RET-200"),
                department(DemoIds.DEPT_HR, "Human Resources", "HR-300"),
                department(DemoIds.DEPT_PAYROLL, "Payroll", "PAY-400")
        ));
        locationRepository.saveAll(java.util.List.of(
                location(DemoIds.LOC_HQ, "Toronto HQ", "America/Toronto", "Ontario"),
                location(DemoIds.LOC_STORE, "Downtown Store", "America/Toronto", "Ontario"),
                location(DemoIds.LOC_WAREHOUSE, "Warehouse", "America/Toronto", "Ontario")
        ));
        jobTitleRepository.saveAll(java.util.List.of(
                title(DemoIds.TITLE_ADMIN, "Platform Administrator", "Admin"),
                title(DemoIds.TITLE_HR_ADMIN, "HR Operations Lead", "Lead"),
                title(DemoIds.TITLE_STORE_MANAGER, "Store Manager", "Manager"),
                title(DemoIds.TITLE_ASSOCIATE, "Retail Associate", "Hourly"),
                title(DemoIds.TITLE_PAYROLL, "Payroll Analyst", "Analyst")
        ));

        employeeRepository.saveAll(java.util.List.of(
                employee(DemoIds.EMP_ADMIN, "NS-001", "Alex", "Rivera", "admin@demo.hcm.local", EmployeeStatus.ACTIVE, EmploymentType.FULL_TIME, DemoIds.DEPT_OPERATIONS, DemoIds.LOC_HQ, DemoIds.TITLE_ADMIN, null, DemoIds.ADMIN_USER, "62.00", "40", LocalDate.of(2021, 4, 5)),
                employee(DemoIds.EMP_HR, "NS-002", "Priya", "Shah", "hr@demo.hcm.local", EmployeeStatus.ACTIVE, EmploymentType.FULL_TIME, DemoIds.DEPT_HR, DemoIds.LOC_HQ, DemoIds.TITLE_HR_ADMIN, DemoIds.EMP_ADMIN, DemoIds.HR_USER, "48.50", "40", LocalDate.of(2020, 9, 14)),
                employee(DemoIds.EMP_MANAGER, "NS-003", "Maya", "Thompson", "manager@demo.hcm.local", EmployeeStatus.ACTIVE, EmploymentType.FULL_TIME, DemoIds.DEPT_RETAIL, DemoIds.LOC_STORE, DemoIds.TITLE_STORE_MANAGER, DemoIds.EMP_HR, DemoIds.MANAGER_USER, "42.00", "40", LocalDate.of(2019, 2, 18)),
                employee(DemoIds.EMP_EMPLOYEE, "NS-004", "Jordan", "Kim", "employee@demo.hcm.local", EmployeeStatus.ACTIVE, EmploymentType.PART_TIME, DemoIds.DEPT_RETAIL, DemoIds.LOC_STORE, DemoIds.TITLE_ASSOCIATE, DemoIds.EMP_MANAGER, DemoIds.EMPLOYEE_USER, "24.75", "28", LocalDate.of(2022, 6, 7)),
                employee(DemoIds.EMP_PAYROLL, "NS-005", "Sam", "Carter", "payroll@demo.hcm.local", EmployeeStatus.ACTIVE, EmploymentType.FULL_TIME, DemoIds.DEPT_PAYROLL, DemoIds.LOC_HQ, DemoIds.TITLE_PAYROLL, DemoIds.EMP_HR, DemoIds.PAYROLL_USER, "45.25", "40", LocalDate.of(2021, 11, 1)),
                employee(DemoIds.EMP_PART_TIME, "NS-006", "Elena", "Garcia", "elena.garcia@northstar.example", EmployeeStatus.ON_LEAVE, EmploymentType.PART_TIME, DemoIds.DEPT_RETAIL, DemoIds.LOC_STORE, DemoIds.TITLE_ASSOCIATE, DemoIds.EMP_MANAGER, null, "22.50", "24", LocalDate.of(2023, 3, 13)),
                employee(DemoIds.EMP_ASSOCIATE, "NS-007", "Amara", "Singh", "amara.singh@northstar.example", EmployeeStatus.ACTIVE, EmploymentType.PART_TIME, DemoIds.DEPT_RETAIL, DemoIds.LOC_STORE, DemoIds.TITLE_ASSOCIATE, DemoIds.EMP_MANAGER, null, "23.25", "28", LocalDate.of(2024, 1, 8))
        ));

        leaveAccrualRuleRepository.saveAll(java.util.List.of(
                accrualRule(DemoIds.LEAVE_RULE_FT_VACATION, EmploymentType.FULL_TIME, "VACATION", "10.00", "160.00"),
                accrualRule(DemoIds.LEAVE_RULE_FT_SICK, EmploymentType.FULL_TIME, "SICK", "4.00", "80.00"),
                accrualRule(DemoIds.LEAVE_RULE_PT_VACATION, EmploymentType.PART_TIME, "VACATION", "5.00", "80.00"),
                accrualRule(DemoIds.LEAVE_RULE_PT_SICK, EmploymentType.PART_TIME, "SICK", "2.00", "40.00")
        ));
        leaveBalanceRepository.saveAll(java.util.List.of(
                leaveBalance(DemoIds.LEAVE_BALANCE_JORDAN_VACATION, DemoIds.EMP_EMPLOYEE, "Jordan Kim", "VACATION", "58.00", "8.00", "16.00", "80.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_JORDAN_SICK, DemoIds.EMP_EMPLOYEE, "Jordan Kim", "SICK", "22.00", "0.00", "0.00", "40.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_AMARA_VACATION, DemoIds.EMP_ASSOCIATE, "Amara Singh", "VACATION", "8.00", "0.00", "0.00", "80.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_AMARA_SICK, DemoIds.EMP_ASSOCIATE, "Amara Singh", "SICK", "14.00", "2.00", "8.00", "40.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_MAYA_VACATION, DemoIds.EMP_MANAGER, "Maya Thompson", "VACATION", "96.00", "16.00", "0.00", "160.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_MAYA_SICK, DemoIds.EMP_MANAGER, "Maya Thompson", "SICK", "34.00", "4.00", "0.00", "80.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_ELENA_VACATION, DemoIds.EMP_PART_TIME, "Elena Garcia", "VACATION", "12.00", "8.00", "0.00", "80.00"),
                leaveBalance(DemoIds.LEAVE_BALANCE_ELENA_SICK, DemoIds.EMP_PART_TIME, "Elena Garcia", "SICK", "9.00", "3.00", "0.00", "40.00")
        ));

        var weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        scheduleWeekRepository.saveAll(java.util.List.of(
                scheduleWeek(DemoIds.SCHEDULE_WEEK_PREVIOUS, weekStart.minusWeeks(1), ScheduleWeekStatus.PUBLISHED, DemoIds.MANAGER_USER),
                scheduleWeek(DemoIds.SCHEDULE_WEEK_CURRENT, weekStart, ScheduleWeekStatus.DRAFT, null)
        ));
        scheduleAlertRepository.saveAll(java.util.List.of(
                alert(null, "Jordan Kim", weekStart, "OVERLAP", "HIGH", "Jordan Kim has overlapping shifts on Wednesday.", "OPEN"),
                alert(null, "Elena Garcia", weekStart, "APPROVED_LEAVE", "HIGH", "Elena Garcia is scheduled during approved leave on Monday.", "OPEN"),
                alert(null, "Open Shift", weekStart, "OPEN_SHIFT", "MEDIUM", "An open shift remains on Friday from 16:00 to 22:00.", "OPEN")
        ));
        shiftRepository.saveAll(java.util.List.of(
                shift(DemoIds.SHIFT_PREVIOUS_JORDAN, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart.minusWeeks(1), LocalTime.of(9, 0), LocalTime.of(17, 0), "ASSIGNED", true),
                shift(DemoIds.SHIFT_PREVIOUS_AMARA, DemoIds.EMP_ASSOCIATE, "Amara Singh", weekStart.minusWeeks(1).plusDays(2), LocalTime.of(10, 0), LocalTime.of(18, 0), "ASSIGNED", true),
                shift(DemoIds.SHIFT_CURRENT_JORDAN_TUE, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart.plusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0), "ASSIGNED", false),
                shift(DemoIds.SHIFT_CURRENT_JORDAN_WED, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart.plusDays(2), LocalTime.of(9, 0), LocalTime.of(15, 0), "ASSIGNED", false),
                shift(DemoIds.SHIFT_CURRENT_JORDAN_OVERLAP, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart.plusDays(2), LocalTime.of(14, 0), LocalTime.of(20, 0), "ASSIGNED", false),
                shift(DemoIds.SHIFT_CURRENT_ELENA_LEAVE, DemoIds.EMP_PART_TIME, "Elena Garcia", weekStart, LocalTime.of(10, 0), LocalTime.of(16, 0), "ASSIGNED", false),
                shift(DemoIds.SHIFT_CURRENT_OPEN, null, "Open Shift", weekStart.plusDays(4), LocalTime.of(16, 0), LocalTime.of(22, 0), "OPEN", false)
        ));

        payRuleConfigRepository.saveAll(java.util.List.of(
                payRule(DemoIds.PAY_RULE_DEFAULT, null, "Northstar default weekly rule", LocalDate.of(2020, 1, 1), "40.00", "1.50", "1.50", true),
                payRule(DemoIds.PAY_RULE_STORE, DemoIds.LOC_STORE, "Downtown Store holiday premium rule", LocalDate.of(2020, 1, 1), "40.00", "1.50", "1.50", true)
        ));
        payrollHolidayRepository.save(payrollHoliday(DemoIds.PAYROLL_HOLIDAY_STORE, DemoIds.LOC_STORE, weekStart.minusWeeks(1).plusDays(2), "Ontario Retail Heritage Day"));

        timesheetRepository.saveAll(java.util.List.of(
                timesheet(DemoIds.TIMESHEET_JORDAN_CURRENT, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart, "11.40", "0.00", TimesheetStatus.DRAFT, false, "Open punch waiting for clock-out demo."),
                timesheet(DemoIds.TIMESHEET_JORDAN_PRIOR, DemoIds.EMP_EMPLOYEE, "Jordan Kim", weekStart.minusWeeks(1), "15.38", "0.00", TimesheetStatus.SUBMITTED, false, "Missed punch was corrected before payroll preview generation."),
                timesheet(DemoIds.TIMESHEET_ELENA_PRIOR, DemoIds.EMP_PART_TIME, "Elena Garcia", weekStart.minusWeeks(1), "5.50", "0.00", TimesheetStatus.APPROVED, true, "Approved before medical leave started."),
                timesheet(DemoIds.TIMESHEET_MAYA_PRIOR, DemoIds.EMP_MANAGER, "Maya Thompson", weekStart.minusWeeks(1), "40.00", "2.00", TimesheetStatus.CHANGE_REQUESTED, false, "Review early close exception on Thursday."),
                timesheet(DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", weekStart.minusWeeks(1), "40.00", "4.00", TimesheetStatus.SUBMITTED, false, "Overtime and holiday premium example for payroll preview.")
        ));
        timeEntryRepository.saveAll(java.util.List.of(
                timeEntry(DemoIds.TIME_ENTRY_JORDAN_ACTIVE, DemoIds.TIMESHEET_JORDAN_CURRENT, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.SHIFT_CURRENT_JORDAN_TUE, weekStart.plusDays(1), weekStart.plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), null, TimeEntrySource.CLOCK, TimeEntryStatus.OPEN, "Active clock-in for the employee demo."),
                timeEntry(DemoIds.TIME_ENTRY_JORDAN_PRIOR_MISSED, DemoIds.TIMESHEET_JORDAN_PRIOR, DemoIds.EMP_EMPLOYEE, "Jordan Kim", null, weekStart.minusWeeks(1), weekStart.minusWeeks(1).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).atTime(17, 30).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CORRECTION, TimeEntryStatus.COMPLETE, "Missed punch corrected before payroll preview."),
                timeEntry(DemoIds.TIME_ENTRY_JORDAN_PRIOR_MANUAL, DemoIds.TIMESHEET_JORDAN_PRIOR, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.SHIFT_PREVIOUS_JORDAN, weekStart.minusWeeks(1).plusDays(1), weekStart.minusWeeks(1).plusDays(1).atTime(9, 12).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(1).atTime(17, 5).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.MANUAL, TimeEntryStatus.COMPLETE, "Manual correction entered before submission."),
                timeEntry(DemoIds.TIME_ENTRY_ELENA_LOCKED, DemoIds.TIMESHEET_ELENA_PRIOR, DemoIds.EMP_PART_TIME, "Elena Garcia", null, weekStart.minusWeeks(1).plusDays(2), weekStart.minusWeeks(1).plusDays(2).atTime(10, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(2).atTime(15, 30).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Locked approved period."),
                timeEntry(DemoIds.TIME_ENTRY_MAYA_CHANGE, DemoIds.TIMESHEET_MAYA_PRIOR, DemoIds.EMP_MANAGER, "Maya Thompson", null, weekStart.minusWeeks(1).plusDays(3), weekStart.minusWeeks(1).plusDays(3).atTime(8, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(3).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CORRECTION, TimeEntryStatus.COMPLETE, "Early close exception under review."),
                timeEntry(DemoIds.TIME_ENTRY_JORDAN_LATE, DemoIds.TIMESHEET_JORDAN_CURRENT, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.SHIFT_CURRENT_JORDAN_WED, weekStart.plusDays(2), weekStart.plusDays(2).atTime(9, 18).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.plusDays(2).atTime(15, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Late arrival warning tied to schedule."),
                timeEntry(DemoIds.TIME_ENTRY_JORDAN_EARLY, DemoIds.TIMESHEET_JORDAN_CURRENT, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.SHIFT_CURRENT_JORDAN_OVERLAP, weekStart.plusDays(2), weekStart.plusDays(2).atTime(14, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.plusDays(2).atTime(19, 42).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Early departure warning tied to schedule."),
                timeEntry(DemoIds.TIME_ENTRY_AMARA_PRIOR_MON, DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, weekStart.minusWeeks(1), weekStart.minusWeeks(1).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Payroll preview regular hour example."),
                timeEntry(DemoIds.TIME_ENTRY_AMARA_PRIOR_TUE, DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, weekStart.minusWeeks(1).plusDays(1), weekStart.minusWeeks(1).plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(1).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Payroll preview regular hour example."),
                timeEntry(DemoIds.TIME_ENTRY_AMARA_PRIOR_WED, DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, weekStart.minusWeeks(1).plusDays(2), weekStart.minusWeeks(1).plusDays(2).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(2).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Worked holiday premium example."),
                timeEntry(DemoIds.TIME_ENTRY_AMARA_PRIOR_THU, DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, weekStart.minusWeeks(1).plusDays(3), weekStart.minusWeeks(1).plusDays(3).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(3).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Unpaid break deduction example."),
                timeEntry(DemoIds.TIME_ENTRY_AMARA_PRIOR_FRI, DemoIds.TIMESHEET_AMARA_PRIOR, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, weekStart.minusWeeks(1).plusDays(4), weekStart.minusWeeks(1).plusDays(4).atTime(9, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(4).atTime(18, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), TimeEntrySource.CLOCK, TimeEntryStatus.COMPLETE, "Overtime threshold example.")
        ));
        timeBreakRepository.saveAll(java.util.List.of(
                timeBreak(DemoIds.TIME_BREAK_JORDAN_PRIOR_MONDAY, DemoIds.TIME_ENTRY_JORDAN_PRIOR_MISSED, weekStart.minusWeeks(1).atTime(12, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).atTime(12, 30).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), 30, TimeBreakSource.CORRECTION, "Corrected lunch break."),
                timeBreak(DemoIds.TIME_BREAK_JORDAN_PRIOR, DemoIds.TIME_ENTRY_JORDAN_PRIOR_MANUAL, weekStart.minusWeeks(1).plusDays(1).atTime(12, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(1).atTime(12, 30).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), 30, TimeBreakSource.MANUAL, "Corrected lunch break."),
                timeBreak(DemoIds.TIME_BREAK_AMARA_PRIOR_THU, DemoIds.TIME_ENTRY_AMARA_PRIOR_THU, weekStart.minusWeeks(1).plusDays(3).atTime(13, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), weekStart.minusWeeks(1).plusDays(3).atTime(14, 0).atZone(java.time.ZoneId.of("America/Toronto")).toInstant(), 60, TimeBreakSource.CLOCK, "Unpaid lunch break deducted from payroll preview.")
        ));
        changeRequestRepository.save(changeRequest(DemoIds.CHANGE_REQUEST_MAYA, DemoIds.TIMESHEET_MAYA_PRIOR, DemoIds.MANAGER_USER, "manager@demo.hcm.local", "Need to correct early close exception.", TimesheetChangeRequestStatus.APPROVED, "Approved for correction."));

        var seededPreview = PayrollPreview.builder()
                .tenantId(DemoIds.TENANT)
                .periodStart(weekStart.minusWeeks(1))
                .periodEnd(weekStart.minusDays(1))
                .locationId(null)
                .regularHours(new BigDecimal("60.88"))
                .overtimeHours(new BigDecimal("4.00"))
                .unpaidBreakHours(new BigDecimal("2.00"))
                .unpaidLeaveHours(new BigDecimal("4.00"))
                .holidayHours(new BigDecimal("14.50"))
                .holidayPremiumPay(new BigDecimal("166.51"))
                .grossPay(new BigDecimal("1740.42"))
                .employeeCount(3)
                .timesheetCount(3)
                .status("READY_FOR_REVIEW")
                .generatedByUserId(DemoIds.PAYROLL_USER)
                .explanation("Seeded payroll preview from 3 submitted/approved timesheets: 60.88 regular hours, 4 overtime hours, 2 unpaid break hours deducted, 14.5 holiday hours generated $166.51 premium, and 4 approved unpaid leave hours were excluded.")
                .metadata("{\"source\":\"demo-seed\",\"period\":\"previous-week\"}")
                .build();
        seededPreview.setId(DemoIds.PAYROLL_PREVIEW_PREVIOUS);
        payrollPreviewRepository.save(seededPreview);
        payrollPreviewLineRepository.saveAll(java.util.List.of(
                payrollLine(DemoIds.PAYROLL_LINE_JORDAN, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.LOC_STORE, "Downtown Store", 1, "24.75", "15.38", "0.00", "0.00", "1.00", "0.00", "380.66", "0.00", "0.00", "380.66", "Downtown Store holiday premium rule", "Jordan Kim: 15.38 regular hours and 0 overtime hours across 1 submitted/approved timesheet at $24.75/hr. 1 unpaid break hour was deducted; missed punch correction is clean for payroll preview."),
                payrollLine(DemoIds.PAYROLL_LINE_ELENA, DemoIds.EMP_PART_TIME, "Elena Garcia", DemoIds.LOC_STORE, "Downtown Store", 1, "22.50", "5.50", "0.00", "5.50", "0.00", "4.00", "123.75", "0.00", "61.88", "185.63", "Downtown Store holiday premium rule", "Elena Garcia: 5.5 holiday worked hours added $61.88 premium. 4 approved unpaid leave hours were excluded from gross pay."),
                payrollLine(DemoIds.PAYROLL_LINE_AMARA, DemoIds.EMP_ASSOCIATE, "Amara Singh", DemoIds.LOC_STORE, "Downtown Store", 1, "23.25", "40.00", "4.00", "9.00", "1.00", "0.00", "930.00", "139.50", "104.63", "1174.13", "Downtown Store holiday premium rule", "Amara Singh: 40 regular hours and 4 overtime hours from a 44-hour payable week. 1 unpaid break hour was deducted and 9 holiday hours added $104.63 premium.")
        ));

        leaveRequestRepository.saveAll(java.util.List.of(
                leave(DemoIds.LEAVE_REQUEST_JORDAN_PENDING, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.EMPLOYEE_USER, "VACATION", weekStart.plusDays(1), weekStart.plusDays(2), "16.00", "PENDING", "Family trip request.", null, null, 3, "Conflicts with 3 scheduled shift(s): " + weekStart.plusDays(1) + ", " + weekStart.plusDays(2)),
                leave(DemoIds.LEAVE_REQUEST_AMARA_SICK, DemoIds.EMP_ASSOCIATE, "Amara Singh", null, "SICK", weekStart.plusDays(4), weekStart.plusDays(4), "8.00", "PENDING", "Doctor appointment.", null, null, 0, null),
                leave(DemoIds.LEAVE_REQUEST_ELENA_PAYROLL, DemoIds.EMP_PART_TIME, "Elena Garcia", null, "UNPAID", weekStart.minusWeeks(1).plusDays(4), weekStart.minusWeeks(1).plusDays(4), "4.00", "APPROVED", "Approved unpaid leave; excluded from payroll preview.", DemoIds.MANAGER_USER, "Approved before payroll preview.", 0, null),
                leave(DemoIds.LEAVE_REQUEST_ELENA_CURRENT, DemoIds.EMP_PART_TIME, "Elena Garcia", null, "UNPAID", weekStart, weekStart.plusDays(4), "30.00", "APPROVED", "Approved unpaid leave; excluded from payroll preview.", DemoIds.MANAGER_USER, "Approved medical leave.", 1, "Conflicts with 1 scheduled shift(s): " + weekStart),
                leave(DemoIds.LEAVE_REQUEST_MAYA_REJECTED, DemoIds.EMP_MANAGER, "Maya Thompson", DemoIds.MANAGER_USER, "VACATION", weekStart.plusWeeks(1).plusDays(1), weekStart.plusWeeks(1).plusDays(2), "16.00", "REJECTED", "Conference travel request.", DemoIds.HR_USER, "Coverage not available that week.", 0, null)
        ));
        leaveBalanceEventRepository.saveAll(java.util.List.of(
                leaveEvent(DemoIds.LEAVE_EVENT_JORDAN_SEED, DemoIds.LEAVE_BALANCE_JORDAN_VACATION, DemoIds.EMP_EMPLOYEE, "Jordan Kim", null, "VACATION", "MANUAL_ADJUSTMENT", weekStart.minusDays(14), null, "58.00", "34.00", "Seeded starting balance."),
                leaveEvent(DemoIds.LEAVE_EVENT_JORDAN_RESERVE, DemoIds.LEAVE_BALANCE_JORDAN_VACATION, DemoIds.EMP_EMPLOYEE, "Jordan Kim", DemoIds.LEAVE_REQUEST_JORDAN_PENDING, "VACATION", "REQUEST_RESERVED", weekStart, null, "16.00", "34.00", "Pending vacation request reserved balance."),
                leaveEvent(DemoIds.LEAVE_EVENT_AMARA_RESERVE, DemoIds.LEAVE_BALANCE_AMARA_SICK, DemoIds.EMP_ASSOCIATE, "Amara Singh", DemoIds.LEAVE_REQUEST_AMARA_SICK, "SICK", "REQUEST_RESERVED", weekStart, null, "8.00", "4.00", "Pending sick request reserved balance."),
                leaveEvent(DemoIds.LEAVE_EVENT_MAYA_RELEASE, DemoIds.LEAVE_BALANCE_MAYA_VACATION, DemoIds.EMP_MANAGER, "Maya Thompson", DemoIds.LEAVE_REQUEST_MAYA_REJECTED, "VACATION", "REQUEST_REJECTED_RELEASED", weekStart.minusDays(2), null, "16.00", "80.00", "Rejected vacation request released pending balance.")
        ));

        importJobRepository.saveAll(java.util.List.of(
                importJob(DemoIds.IMPORT_JOB_COMPLETED, "northstar-new-hires-may.csv", "COMPLETED_WITH_ERRORS", 4, 1, 3, 1, "1 employee imported. 3 rows retained with validation errors.", "row,field,message\n3,employeeNumber,Duplicate employee ID in file\n4,managerEmail,Invalid manager email\n5,hourlyRate,Invalid pay rate\n"),
                importJob(DemoIds.IMPORT_JOB_PREVIEW, "northstar-preview-june.csv", "PREVIEW_READY", 3, 1, 2, 0, "1 row ready to import. 2 rows need correction.", "row,field,message\n3,department,Invalid department\n3,status,Invalid employment status\n4,employeeNumber,Employee ID conflicts with an existing employee\n")
        ));
        employeeImportRowRepository.saveAll(java.util.List.of(
                importRow(DemoIds.IMPORT_ROW_COMPLETED_VALID, DemoIds.IMPORT_JOB_COMPLETED, 2, "IMPORTED", "{\"employeeNumber\":\"NS-020\",\"firstName\":\"Noah\",\"lastName\":\"Patel\",\"workEmail\":\"noah.patel@northstar.example\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"21.50\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-05-01\"}", "[]", null),
                importRow(DemoIds.IMPORT_ROW_DUPLICATE, DemoIds.IMPORT_JOB_COMPLETED, 3, "ERROR", "{\"employeeNumber\":\"NS-020\",\"firstName\":\"Duplicate\",\"lastName\":\"Row\",\"workEmail\":\"duplicate.row@northstar.example\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"21.50\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-05-01\"}", "[{\"rowNumber\":3,\"field\":\"employeeNumber\",\"message\":\"Duplicate employee ID in file\"}]", null),
                importRow(DemoIds.IMPORT_ROW_BAD_MANAGER, DemoIds.IMPORT_JOB_COMPLETED, 4, "ERROR", "{\"employeeNumber\":\"NS-021\",\"firstName\":\"Ivy\",\"lastName\":\"Chen\",\"workEmail\":\"ivy.chen@northstar.example\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"FULL_TIME\",\"managerEmail\":\"missing.manager@northstar.example\",\"hourlyRate\":\"26.00\",\"weeklyHourCap\":\"40\",\"hireDate\":\"2026-05-02\"}", "[{\"rowNumber\":4,\"field\":\"managerEmail\",\"message\":\"Invalid manager email\"}]", null),
                importRow(DemoIds.IMPORT_ROW_BAD_PAY, DemoIds.IMPORT_JOB_COMPLETED, 5, "ERROR", "{\"employeeNumber\":\"NS-022\",\"firstName\":\"Leo\",\"lastName\":\"Morgan\",\"workEmail\":\"leo.morgan@northstar.example\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"-2\",\"weeklyHourCap\":\"20\",\"hireDate\":\"2026-05-03\"}", "[{\"rowNumber\":5,\"field\":\"hourlyRate\",\"message\":\"Invalid pay rate\"}]", null),
                importRow(DemoIds.IMPORT_ROW_PREVIEW_VALID, DemoIds.IMPORT_JOB_PREVIEW, 2, "VALID", "{\"employeeNumber\":\"NS-023\",\"firstName\":\"Riley\",\"lastName\":\"Brooks\",\"workEmail\":\"riley.brooks@northstar.example\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"22.00\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-06-01\"}", "[]", null),
                importRow(DemoIds.IMPORT_ROW_BAD_DEPT_STATUS, DemoIds.IMPORT_JOB_PREVIEW, 3, "ERROR", "{\"employeeNumber\":\"NS-024\",\"firstName\":\"Talia\",\"lastName\":\"Stone\",\"workEmail\":\"talia.stone@northstar.example\",\"department\":\"Merch\",\"location\":\"Downtown Store\",\"status\":\"STARTED\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"23.00\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-06-02\"}", "[{\"rowNumber\":3,\"field\":\"department\",\"message\":\"Invalid department\"},{\"rowNumber\":3,\"field\":\"status\",\"message\":\"Invalid employment status\"}]", null),
                importRow(DemoIds.IMPORT_ROW_EXISTING_CONFLICT, DemoIds.IMPORT_JOB_PREVIEW, 4, "ERROR", "{\"employeeNumber\":\"NS-004\",\"firstName\":\"Existing\",\"lastName\":\"Conflict\",\"workEmail\":\"employee@demo.hcm.local\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"hourlyRate\":\"22.00\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-06-03\"}", "[{\"rowNumber\":4,\"field\":\"employeeNumber\",\"message\":\"Employee ID conflicts with an existing employee\"},{\"rowNumber\":4,\"field\":\"workEmail\",\"message\":\"Work email conflicts with an existing employee\"}]", null)
        ));
        webhookEventRepository.saveAll(java.util.List.of(
                webhookEvent(DemoIds.WEBHOOK_EMPLOYEE_UPDATED, "employee.updated", "Employee", DemoIds.EMP_EMPLOYEE, "DELIVERED", "{\"employeeId\":\"%s\",\"employeeNumber\":\"NS-004\",\"fullName\":\"Jordan Kim\",\"status\":\"ACTIVE\"}".formatted(DemoIds.EMP_EMPLOYEE)),
                webhookEvent(DemoIds.WEBHOOK_TIMESHEET_APPROVED, "timesheet.approved", "Timesheet", DemoIds.TIMESHEET_ELENA_PRIOR, "DELIVERED", "{\"timesheetId\":\"%s\",\"employeeName\":\"Elena Garcia\",\"status\":\"APPROVED\"}".formatted(DemoIds.TIMESHEET_ELENA_PRIOR)),
                webhookEvent(DemoIds.WEBHOOK_PAYROLL_GENERATED, "payroll.preview.generated", "PayrollPreview", DemoIds.PAYROLL_PREVIEW_PREVIOUS, "FAILED", "{\"payrollPreviewId\":\"%s\",\"grossPay\":1740.42,\"employeeCount\":3}".formatted(DemoIds.PAYROLL_PREVIEW_PREVIOUS))
        ));
        webhookDeliveryAttemptRepository.saveAll(java.util.List.of(
                webhookAttempt(DemoIds.WEBHOOK_ATTEMPT_EMPLOYEE, DemoIds.WEBHOOK_EMPLOYEE_UPDATED, "DELIVERED", 202, "Simulated 202 Accepted delivery"),
                webhookAttempt(DemoIds.WEBHOOK_ATTEMPT_TIMESHEET, DemoIds.WEBHOOK_TIMESHEET_APPROVED, "DELIVERED", 202, "Simulated 202 Accepted delivery"),
                webhookAttempt(DemoIds.WEBHOOK_ATTEMPT_PAYROLL_FAILED, DemoIds.WEBHOOK_PAYROLL_GENERATED, "FAILED", 503, "Demo receiver unavailable; retry from Integration Center")
        ));

        auditLogRepository.saveAll(java.util.List.of(
                audit("system@demo-seed", "demo.seeded", "Tenant", DemoIds.TENANT, null, "{\"tenant\":\"Northstar Retail Group\"}"),
                audit("hr@demo.hcm.local", "employee.updated", "Employee", DemoIds.EMP_EMPLOYEE, "{\"weeklyHourCap\":24}", "{\"weeklyHourCap\":28}"),
                audit("manager@demo.hcm.local", "schedule.published", "ScheduleWeek", null, null, "{\"warnings\":3,\"weekStart\":\"%s\"}".formatted(weekStart)),
                audit("employee@demo.hcm.local", "time.clock.in", "TimeEntry", DemoIds.TIME_ENTRY_JORDAN_ACTIVE, null, "{\"employee\":\"Jordan Kim\"}"),
                audit("employee@demo.hcm.local", "time.entry.created", "Timesheet", DemoIds.TIMESHEET_JORDAN_PRIOR, null, "{\"source\":\"manual-correction\"}"),
                audit("employee@demo.hcm.local", "timesheet.submitted", "Timesheet", DemoIds.TIMESHEET_JORDAN_PRIOR, null, "{\"status\":\"SUBMITTED\"}"),
                audit("manager@demo.hcm.local", "timesheet.approved", "Timesheet", DemoIds.TIMESHEET_ELENA_PRIOR, null, "{\"status\":\"APPROVED\"}"),
                audit("manager@demo.hcm.local", "timesheet.change.approved", "Timesheet", DemoIds.TIMESHEET_MAYA_PRIOR, null, "{\"status\":\"CHANGE_REQUESTED\"}"),
                audit("payroll@demo.hcm.local", "timesheet.locked", "Timesheet", DemoIds.TIMESHEET_ELENA_PRIOR, null, "{\"locked\":true}"),
                audit("payroll@demo.hcm.local", "payroll.preview.generated", "PayrollPreview", DemoIds.PAYROLL_PREVIEW_PREVIOUS, null, "{\"grossPay\":1740.42,\"employeeCount\":3,\"timesheetCount\":3}"),
                audit("employee@demo.hcm.local", "leave.request.submitted", "LeaveRequest", DemoIds.LEAVE_REQUEST_JORDAN_PENDING, null, "{\"leaveType\":\"VACATION\",\"hours\":16.00,\"conflicts\":3}"),
                audit("manager@demo.hcm.local", "leave.request.approved", "LeaveRequest", DemoIds.LEAVE_REQUEST_ELENA_PAYROLL, null, "{\"leaveType\":\"UNPAID\",\"hours\":4.00}"),
                audit("hr@demo.hcm.local", "leave.request.rejected", "LeaveRequest", DemoIds.LEAVE_REQUEST_MAYA_REJECTED, null, "{\"leaveType\":\"VACATION\",\"hours\":16.00}"),
                audit("hr@demo.hcm.local", "leave.accrual.run", "LeaveBalance", null, null, "{\"balancesUpdated\":8,\"hoursAccrued\":42.00}"),
                audit("hr@demo.hcm.local", "employee.import.completed", "ImportJob", DemoIds.IMPORT_JOB_COMPLETED, null, "{\"successRows\":1,\"errorRows\":3}"),
                audit("system@demo-seed", "webhook.delivery.failed", "WebhookEvent", DemoIds.WEBHOOK_PAYROLL_GENERATED, null, "{\"eventType\":\"payroll.preview.generated\",\"status\":\"FAILED\"}")
        ));
    }

    private UserAccount user(UUID id, String email, String displayName, UserRole role) {
        var user = UserAccount.builder()
                .tenantId(DemoIds.TENANT)
                .email(email)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .status(AccountStatus.ACTIVE)
                .protectedDemoAccount(true)
                .roles(new HashSet<>(Set.of(role)))
                .build();
        user.setId(id);
        return user;
    }

    private Department department(UUID id, String name, String costCenter) {
        var department = Department.builder().tenantId(DemoIds.TENANT).name(name).costCenter(costCenter).build();
        department.setId(id);
        return department;
    }

    private Location location(UUID id, String name, String timezone, String region) {
        var location = Location.builder().tenantId(DemoIds.TENANT).name(name).timezone(timezone).region(region).build();
        location.setId(id);
        return location;
    }

    private JobTitle title(UUID id, String name, String careerLevel) {
        var title = JobTitle.builder().tenantId(DemoIds.TENANT).name(name).careerLevel(careerLevel).build();
        title.setId(id);
        return title;
    }

    private Employee employee(UUID id, String number, String firstName, String lastName, String email, EmployeeStatus status, EmploymentType type, UUID departmentId, UUID locationId, UUID titleId, UUID managerId, UUID userId, String hourlyRate, String cap, LocalDate hireDate) {
        var employee = Employee.builder()
                .tenantId(DemoIds.TENANT)
                .employeeNumber(number)
                .firstName(firstName)
                .lastName(lastName)
                .workEmail(email)
                .status(status)
                .employmentType(type)
                .departmentId(departmentId)
                .locationId(locationId)
                .jobTitleId(titleId)
                .managerEmployeeId(managerId)
                .userAccountId(userId)
                .hourlyRate(new BigDecimal(hourlyRate))
                .weeklyHourCap(new BigDecimal(cap))
                .hireDate(hireDate)
                .build();
        employee.setId(id);
        return employee;
    }

    private ScheduleAlert alert(UUID employeeId, String employeeName, LocalDate weekStart, String type, String severity, String message, String status) {
        return ScheduleAlert.builder()
                .tenantId(DemoIds.TENANT)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .weekStartDate(weekStart)
                .alertType(type)
                .severity(severity)
                .message(message)
                .status(status)
                .build();
    }

    private ScheduleWeek scheduleWeek(UUID id, LocalDate weekStart, ScheduleWeekStatus status, UUID publishedByUserId) {
        var week = ScheduleWeek.builder()
                .tenantId(DemoIds.TENANT)
                .weekStartDate(weekStart)
                .status(status)
                .publishedAt(status == ScheduleWeekStatus.PUBLISHED ? Instant.now().minusSeconds(86_400) : null)
                .publishedByUserId(publishedByUserId)
                .build();
        week.setId(id);
        return week;
    }

    private Shift shift(UUID id, UUID employeeId, String employeeName, LocalDate date, LocalTime start, LocalTime end, String status, boolean published) {
        var shift = Shift.builder()
                .tenantId(DemoIds.TENANT)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .departmentId(DemoIds.DEPT_RETAIL)
                .locationId(DemoIds.LOC_STORE)
                .shiftDate(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .published(published)
                .build();
        shift.setId(id);
        return shift;
    }

    private PayRuleConfig payRule(UUID id, UUID locationId, String name, LocalDate effectiveStart, String weeklyRegularHours, String overtimeMultiplier, String holidayMultiplier, boolean deductBreaks) {
        var rule = PayRuleConfig.builder()
                .tenantId(DemoIds.TENANT)
                .locationId(locationId)
                .name(name)
                .effectiveStartDate(effectiveStart)
                .weeklyRegularHours(new BigDecimal(weeklyRegularHours))
                .overtimeMultiplier(new BigDecimal(overtimeMultiplier))
                .holidayPremiumMultiplier(new BigDecimal(holidayMultiplier))
                .unpaidBreaksDeductible(deductBreaks)
                .build();
        rule.setId(id);
        return rule;
    }

    private PayrollHoliday payrollHoliday(UUID id, UUID locationId, LocalDate date, String name) {
        var holiday = PayrollHoliday.builder()
                .tenantId(DemoIds.TENANT)
                .locationId(locationId)
                .holidayDate(date)
                .name(name)
                .build();
        holiday.setId(id);
        return holiday;
    }

    private Timesheet timesheet(UUID id, UUID employeeId, String employeeName, LocalDate weekStart, String regularHours, String overtimeHours, TimesheetStatus status, boolean locked, String note) {
        var timesheet = Timesheet.builder()
                .tenantId(DemoIds.TENANT)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .weekStartDate(weekStart)
                .regularHours(new BigDecimal(regularHours))
                .overtimeHours(new BigDecimal(overtimeHours))
                .status(status)
                .submittedAt(Instant.now().minusSeconds(86_400))
                .approvedAt(status == TimesheetStatus.APPROVED ? Instant.now().minusSeconds(3_600) : null)
                .approverUserId(status == TimesheetStatus.APPROVED ? DemoIds.MANAGER_USER : null)
                .lockedPayPeriod(locked)
                .managerNote(note)
                .build();
        timesheet.setId(id);
        return timesheet;
    }

    private TimeEntry timeEntry(UUID id, UUID timesheetId, UUID employeeId, String employeeName, UUID shiftId, LocalDate entryDate, Instant clockIn, Instant clockOut, TimeEntrySource source, TimeEntryStatus status, String note) {
        var entry = TimeEntry.builder()
                .tenantId(DemoIds.TENANT)
                .timesheetId(timesheetId)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .shiftId(shiftId)
                .entryDate(entryDate)
                .clockInAt(clockIn)
                .clockOutAt(clockOut)
                .source(source)
                .status(status)
                .note(note)
                .build();
        entry.setId(id);
        return entry;
    }

    private TimeBreak timeBreak(UUID id, UUID entryId, Instant start, Instant end, int durationMinutes, TimeBreakSource source, String note) {
        var timeBreak = TimeBreak.builder()
                .tenantId(DemoIds.TENANT)
                .timeEntryId(entryId)
                .breakStartAt(start)
                .breakEndAt(end)
                .durationMinutes(durationMinutes)
                .source(source)
                .note(note)
                .build();
        timeBreak.setId(id);
        return timeBreak;
    }

    private PayrollPreviewLine payrollLine(UUID id, UUID employeeId, String employeeName, UUID locationId, String locationName, int timesheetCount, String hourlyRate, String regularHours, String overtimeHours, String holidayHours, String unpaidBreakHours, String unpaidLeaveHours, String regularPay, String overtimePay, String holidayPremiumPay, String grossPay, String ruleName, String explanation) {
        var line = PayrollPreviewLine.builder()
                .tenantId(DemoIds.TENANT)
                .payrollPreviewId(DemoIds.PAYROLL_PREVIEW_PREVIOUS)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .locationId(locationId)
                .locationName(locationName)
                .timesheetCount(timesheetCount)
                .hourlyRate(new BigDecimal(hourlyRate))
                .regularHours(new BigDecimal(regularHours))
                .overtimeHours(new BigDecimal(overtimeHours))
                .holidayHours(new BigDecimal(holidayHours))
                .unpaidBreakHours(new BigDecimal(unpaidBreakHours))
                .unpaidLeaveHours(new BigDecimal(unpaidLeaveHours))
                .regularPay(new BigDecimal(regularPay))
                .overtimePay(new BigDecimal(overtimePay))
                .holidayPremiumPay(new BigDecimal(holidayPremiumPay))
                .grossPay(new BigDecimal(grossPay))
                .ruleName(ruleName)
                .explanation(explanation)
                .build();
        line.setId(id);
        return line;
    }

    private TimesheetChangeRequest changeRequest(UUID id, UUID timesheetId, UUID requestedByUserId, String requesterEmail, String reason, TimesheetChangeRequestStatus status, String decisionNote) {
        var request = TimesheetChangeRequest.builder()
                .tenantId(DemoIds.TENANT)
                .timesheetId(timesheetId)
                .requestedByUserId(requestedByUserId)
                .requesterEmail(requesterEmail)
                .reason(reason)
                .status(status)
                .decisionNote(decisionNote)
                .decidedByUserId(status == TimesheetChangeRequestStatus.PENDING ? null : DemoIds.MANAGER_USER)
                .decidedAt(status == TimesheetChangeRequestStatus.PENDING ? null : Instant.now().minusSeconds(1_800))
                .build();
        request.setId(id);
        return request;
    }

    private LeaveAccrualRule accrualRule(UUID id, EmploymentType employmentType, String leaveType, String monthlyHours, String maxHours) {
        var rule = LeaveAccrualRule.builder()
                .tenantId(DemoIds.TENANT)
                .employmentType(employmentType.name())
                .leaveType(leaveType)
                .monthlyAccrualHours(new BigDecimal(monthlyHours))
                .maxBalanceHours(new BigDecimal(maxHours))
                .active(true)
                .build();
        rule.setId(id);
        return rule;
    }

    private LeaveBalance leaveBalance(UUID id, UUID employeeId, String employeeName, String leaveType, String accrued, String used, String pending, String max) {
        var balance = LeaveBalance.builder()
                .tenantId(DemoIds.TENANT)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .leaveType(leaveType)
                .accruedHours(new BigDecimal(accrued))
                .usedHours(new BigDecimal(used))
                .pendingHours(new BigDecimal(pending))
                .maxHours(new BigDecimal(max))
                .build();
        balance.setId(id);
        return balance;
    }

    private LeaveRequest leave(UUID id, UUID employeeId, String employeeName, UUID requestedByUserId, String leaveType, LocalDate start, LocalDate end, String hours, String status, String employeeNote, UUID decidedByUserId, String decisionNote, int conflictCount, String conflictSummary) {
        var request = LeaveRequest.builder()
                .tenantId(DemoIds.TENANT)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .requestedByUserId(requestedByUserId)
                .leaveType(leaveType)
                .startDate(start)
                .endDate(end)
                .hours(new BigDecimal(hours))
                .status(status)
                .submittedAt(Instant.now().minusSeconds(86_400))
                .decidedByUserId(decidedByUserId)
                .decidedAt(decidedByUserId == null ? null : Instant.now().minusSeconds(3_600))
                .employeeNote(employeeNote)
                .managerNote(decisionNote == null ? employeeNote : decisionNote)
                .decisionNote(decisionNote)
                .conflictCount(conflictCount)
                .conflictSummary(conflictSummary)
                .build();
        request.setId(id);
        return request;
    }

    private LeaveBalanceEvent leaveEvent(UUID id, UUID balanceId, UUID employeeId, String employeeName, UUID requestId, String leaveType, String eventType, LocalDate eventDate, LocalDate accrualPeriod, String hours, String balanceAfter, String note) {
        var event = LeaveBalanceEvent.builder()
                .tenantId(DemoIds.TENANT)
                .leaveBalanceId(balanceId)
                .employeeId(employeeId)
                .employeeName(employeeName)
                .leaveRequestId(requestId)
                .leaveType(leaveType)
                .eventType(eventType)
                .eventDate(eventDate)
                .accrualPeriod(accrualPeriod)
                .hours(new BigDecimal(hours))
                .balanceAfterHours(new BigDecimal(balanceAfter))
                .note(note)
                .build();
        event.setId(id);
        return event;
    }

    private ImportJob importJob(UUID id, String fileName, String status, int totalRows, int successRows, int errorRows, int committedRows, String summary, String errorReportCsv) {
        var now = Instant.now().minusSeconds(3_600);
        var job = ImportJob.builder()
                .tenantId(DemoIds.TENANT)
                .fileName(fileName)
                .status(status)
                .totalRows(totalRows)
                .successRows(successRows)
                .errorRows(errorRows)
                .committedRows(committedRows)
                .summary(summary)
                .errorReportCsv(errorReportCsv)
                .fieldMappingJson("{\"employeeNumber\":\"Employee ID\",\"firstName\":\"First Name\",\"lastName\":\"Last Name\",\"workEmail\":\"Email\",\"status\":\"Status\",\"employmentType\":\"Employment Type\",\"department\":\"Department\",\"location\":\"Location\",\"jobTitle\":\"Job Title\",\"managerEmail\":\"Manager Email\",\"hourlyRate\":\"Pay Rate\",\"weeklyHourCap\":\"Weekly Cap\",\"hireDate\":\"Hire Date\"}")
                .sourceMetadata("{\"detectedHeaders\":\"Employee ID|First Name|Last Name|Email|Status|Employment Type|Department|Location|Job Title|Manager Email|Pay Rate|Weekly Cap|Hire Date\"}")
                .queuedAt(now.minusSeconds(60))
                .startedAt(now.minusSeconds(45))
                .previewedAt(now.minusSeconds(30))
                .committedAt(committedRows > 0 ? now.minusSeconds(15) : null)
                .completedAt(status.startsWith("COMPLETED") ? now : null)
                .build();
        job.setId(id);
        return job;
    }

    private EmployeeImportRow importRow(UUID id, UUID jobId, int rowNumber, String status, String mappedJson, String errorJson, UUID importedEmployeeId) {
        var row = EmployeeImportRow.builder()
                .tenantId(DemoIds.TENANT)
                .importJobId(jobId)
                .rowNumber(rowNumber)
                .rawJson(mappedJson)
                .mappedJson(mappedJson)
                .status(status)
                .errorJson(errorJson)
                .importedEmployeeId(importedEmployeeId)
                .build();
        row.setId(id);
        return row;
    }

    private WebhookEvent webhookEvent(UUID id, String eventType, String entityType, UUID entityId, String status, String payloadJson) {
        var event = WebhookEvent.builder()
                .tenantId(DemoIds.TENANT)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .payloadJson(payloadJson)
                .status(status)
                .generatedAt(Instant.now().minusSeconds(2_400))
                .build();
        event.setId(id);
        return event;
    }

    private WebhookDeliveryAttempt webhookAttempt(UUID id, UUID eventId, String status, int responseCode, String responseBody) {
        var attempt = WebhookDeliveryAttempt.builder()
                .tenantId(DemoIds.TENANT)
                .webhookEventId(eventId)
                .destinationName("Northstar Demo Receiver")
                .destinationUrl("https://integrations.demo.local/webhooks/northstar")
                .status(status)
                .responseCode(responseCode)
                .responseBody(responseBody)
                .attemptedAt(Instant.now().minusSeconds(2_300))
                .build();
        attempt.setId(id);
        return attempt;
    }

    private AuditLog audit(String actor, String action, String entityType, UUID entityId, String previousValue, String newValue) {
        return AuditLog.builder()
                .tenantId(DemoIds.TENANT)
                .actorEmail(actor)
                .actionType(action)
                .entityType(entityType)
                .entityId(entityId)
                .previousValue(previousValue)
                .newValue(newValue)
                .metadata("{\"demo\":true}")
                .build();
    }
}
