package com.portfolio.hcm.dashboard;

import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.integration.ImportJobRepository;
import com.portfolio.hcm.leave.LeaveBalanceRepository;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.payroll.PayrollPreviewRepository;
import com.portfolio.hcm.schedule.ScheduleAlertRepository;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.tenant.TenantRepository;
import com.portfolio.hcm.time.TimeEntryRepository;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
import com.portfolio.hcm.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.portfolio.hcm.dashboard.DashboardDtos.DashboardResponse;
import static com.portfolio.hcm.dashboard.DashboardDtos.MetricCard;
import static com.portfolio.hcm.dashboard.DashboardDtos.WorkItem;

@Service
public class DashboardService {
    private final CurrentUserService currentUserService;
    private final TenantRepository tenantRepository;
    private final ScheduleAlertRepository scheduleAlertRepository;
    private final TimesheetRepository timesheetRepository;
    private final PayrollPreviewRepository payrollPreviewRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ImportJobRepository importJobRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public DashboardService(
            CurrentUserService currentUserService,
            TenantRepository tenantRepository,
            ScheduleAlertRepository scheduleAlertRepository,
            TimesheetRepository timesheetRepository,
            PayrollPreviewRepository payrollPreviewRepository,
            LeaveRequestRepository leaveRequestRepository,
            ImportJobRepository importJobRepository,
            AuditLogRepository auditLogRepository,
            EmployeeRepository employeeRepository,
            TimeEntryRepository timeEntryRepository,
            LeaveBalanceRepository leaveBalanceRepository
    ) {
        this.currentUserService = currentUserService;
        this.tenantRepository = tenantRepository;
        this.scheduleAlertRepository = scheduleAlertRepository;
        this.timesheetRepository = timesheetRepository;
        this.payrollPreviewRepository = payrollPreviewRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.importJobRepository = importJobRepository;
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse currentDashboard() {
        var user = currentUserService.requireUser();
        var tenant = tenantRepository.findById(user.tenantId()).orElseThrow();
        var alerts = scheduleAlertRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(user.tenantId());
        var pendingTimesheets = timesheetRepository.findByTenantIdAndStatusInAndDeletedFalseOrderByWeekStartDateDesc(user.tenantId(), List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.CHANGE_REQUESTED));
        var previews = payrollPreviewRepository.findByTenantIdAndDeletedFalseOrderByPeriodStartDesc(user.tenantId());
        var leaveRequests = leaveRequestRepository.findByTenantIdAndDeletedFalseOrderByStartDateAsc(user.tenantId());
        var imports = importJobRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(user.tenantId());
        var audits = auditLogRepository.findTop25ByTenantIdAndDeletedFalseOrderByCreatedAtDesc(user.tenantId());

        var metrics = new ArrayList<MetricCard>();
        metrics.add(new MetricCard("Pending approvals", String.valueOf(pendingTimesheets.size()), "warning", "Submitted and change-requested timesheets waiting for review"));
        metrics.add(new MetricCard("Schedule warnings", String.valueOf(alerts.size()), "danger", "Overlaps, overtime warnings, rest-time and coverage issues"));
        metrics.add(new MetricCard("Payroll preview", previews.isEmpty() ? "$0.00" : DashboardDtos.money(previews.get(0).getGrossPay()), "success", previews.isEmpty() ? "No preview generated yet" : previews.get(0).getStatus()));
        metrics.add(new MetricCard("Imports", imports.isEmpty() ? "0" : imports.get(0).getSuccessRows() + "/" + imports.get(0).getTotalRows(), "info", imports.isEmpty() ? "No import history" : imports.get(0).getStatus()));
        metrics.add(new MetricCard("Leave queue", String.valueOf(leaveRequests.stream().filter(item -> item.getStatus().equals("PENDING")).count()), "warning", "Requests awaiting manager approval"));

        var work = new ArrayList<WorkItem>();
        alerts.stream().limit(3).forEach(alert -> work.add(new WorkItem("Schedule", alert.getAlertType(), alert.getMessage(), alert.getSeverity())));
        pendingTimesheets.stream().limit(3).forEach(timesheet -> work.add(new WorkItem("Timesheet", "Review " + timesheet.getEmployeeName(), timesheet.getRegularHours() + " regular + " + timesheet.getOvertimeHours() + " overtime hours", "MEDIUM")));
        leaveRequests.stream().filter(item -> item.getStatus().equals("PENDING")).limit(2).forEach(leave -> work.add(new WorkItem("Leave", leave.getEmployeeName() + " " + leave.getLeaveType(), leave.getStartDate() + " to " + leave.getEndDate(), "MEDIUM")));
        imports.stream().limit(1).forEach(job -> work.add(new WorkItem("Integration", job.getFileName(), job.getSummary(), job.getErrorRows() > 0 ? "MEDIUM" : "LOW")));
        audits.stream().limit(2).forEach(audit -> work.add(new WorkItem("Audit", audit.getActionType(), audit.getActorEmail() + " changed " + audit.getEntityType(), "LOW")));

        employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(user.tenantId(), user.userId()).ifPresent(employee -> {
            var activeEntry = timeEntryRepository.findFirstByTenantIdAndEmployeeIdAndClockOutAtIsNullAndDeletedFalseOrderByClockInAtDesc(user.tenantId(), employee.getId());
            var personalTimesheets = timesheetRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByWeekStartDateDesc(user.tenantId(), employee.getId());
            var balances = leaveBalanceRepository.findByTenantIdAndEmployeeIdAndDeletedFalseOrderByLeaveTypeAsc(user.tenantId(), employee.getId());
            var ownLeave = leaveRequests.stream().filter(request -> request.getEmployeeId().equals(employee.getId())).toList();
            var availableLeave = balances.stream()
                    .map(balance -> balance.getAccruedHours().subtract(balance.getUsedHours()).subtract(balance.getPendingHours()).max(java.math.BigDecimal.ZERO))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            metrics.add(new MetricCard("Clock status", activeEntry.isPresent() ? "Clocked in" : "Clocked out", activeEntry.isPresent() ? "success" : "info", activeEntry.map(entry -> "Active since " + entry.getClockInAt()).orElse("No active punch")));
            metrics.add(new MetricCard("Leave balance", DashboardDtos.hours(availableLeave) + "h", "info", "Vacation and sick hours available after pending requests"));
            personalTimesheets.stream().findFirst().ifPresent(timesheet -> work.add(new WorkItem("My time", timesheet.getStatus().name(), timesheet.getRegularHours() + " regular + " + timesheet.getOvertimeHours() + " overtime hours", timesheet.getStatus() == TimesheetStatus.REJECTED ? "HIGH" : "LOW")));
            ownLeave.stream().findFirst().ifPresent(leave -> work.add(new WorkItem("My leave", leave.getStatus(), leave.getLeaveType() + " " + leave.getStartDate() + " to " + leave.getEndDate(), leave.getConflictCount() > 0 ? "MEDIUM" : "LOW")));
        });

        return new DashboardResponse(tenant.getName(), user.roles(), metrics, work, quickActions(user.roles()), Instant.now());
    }

    private List<String> quickActions(java.util.Set<UserRole> roles) {
        if (roles.contains(UserRole.SYSTEM_ADMIN)) {
            return List.of("Reset Demo Data", "Review tenant audit log", "Open Swagger API docs");
        }
        if (roles.contains(UserRole.MANAGER)) {
            return List.of("Resolve schedule conflicts", "Review submitted timesheets", "Review leave requests");
        }
        if (roles.contains(UserRole.HR_ADMIN)) {
            return List.of("Add employee", "Review CSV import errors", "Inspect employee audit history");
        }
        if (roles.contains(UserRole.PAYROLL_ADMIN)) {
            return List.of("Generate payroll preview", "Export approved timesheets", "Review payroll explanations");
        }
        return List.of("Clock in/out", "Submit weekly timesheet", "Request leave");
    }
}
