package com.portfolio.hcm.payroll;

import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.integration.WebhookEventService;
import com.portfolio.hcm.leave.LeaveRequestRepository;
import com.portfolio.hcm.org.Location;
import com.portfolio.hcm.org.LocationRepository;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.time.TimeBreak;
import com.portfolio.hcm.time.TimeBreakRepository;
import com.portfolio.hcm.time.TimeEntry;
import com.portfolio.hcm.time.TimeEntryRepository;
import com.portfolio.hcm.time.TimeEntryStatus;
import com.portfolio.hcm.time.Timesheet;
import com.portfolio.hcm.time.TimesheetRepository;
import com.portfolio.hcm.time.TimesheetStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDetailDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewLineDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewRequest;

@Service
public class PayrollPreviewService {
    private static final ZoneId DEMO_ZONE = ZoneId.of("America/Toronto");
    private static final List<TimesheetStatus> PAYROLL_STATUSES = List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED);

    private final PayrollPreviewRepository payrollPreviewRepository;
    private final PayrollPreviewLineRepository payrollPreviewLineRepository;
    private final PayRuleConfigRepository payRuleConfigRepository;
    private final PayrollHolidayRepository payrollHolidayRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeBreakRepository timeBreakRepository;
    private final EmployeeRepository employeeRepository;
    private final LocationRepository locationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollPreviewCalculator calculator;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final WebhookEventService webhookEventService;

    public PayrollPreviewService(
            PayrollPreviewRepository payrollPreviewRepository,
            PayrollPreviewLineRepository payrollPreviewLineRepository,
            PayRuleConfigRepository payRuleConfigRepository,
            PayrollHolidayRepository payrollHolidayRepository,
            TimesheetRepository timesheetRepository,
            TimeEntryRepository timeEntryRepository,
            TimeBreakRepository timeBreakRepository,
            EmployeeRepository employeeRepository,
            LocationRepository locationRepository,
            LeaveRequestRepository leaveRequestRepository,
            PayrollPreviewCalculator calculator,
            CurrentUserService currentUserService,
            AuditService auditService,
            WebhookEventService webhookEventService
    ) {
        this.payrollPreviewRepository = payrollPreviewRepository;
        this.payrollPreviewLineRepository = payrollPreviewLineRepository;
        this.payRuleConfigRepository = payRuleConfigRepository;
        this.payrollHolidayRepository = payrollHolidayRepository;
        this.timesheetRepository = timesheetRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.timeBreakRepository = timeBreakRepository;
        this.employeeRepository = employeeRepository;
        this.locationRepository = locationRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.calculator = calculator;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.webhookEventService = webhookEventService;
    }

    @Transactional(readOnly = true)
    public List<PayrollPreviewDto> list() {
        return payrollPreviewRepository.findByTenantIdAndDeletedFalseOrderByPeriodStartDesc(currentUserService.tenantId()).stream()
                .map(PayrollPreviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PayrollPreviewDetailDto detail(UUID id) {
        var tenantId = currentUserService.tenantId();
        var preview = payrollPreviewRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll preview not found"));
        var lines = payrollPreviewLineRepository.findByTenantIdAndPayrollPreviewIdAndDeletedFalseOrderByEmployeeNameAsc(tenantId, preview.getId()).stream()
                .map(PayrollPreviewLineDto::from)
                .toList();
        return new PayrollPreviewDetailDto(PayrollPreviewDto.from(preview), lines);
    }

    @Transactional
    public PayrollPreviewDetailDto generate(PayrollPreviewRequest request) {
        var user = currentUserService.requireUser();
        var period = resolvePeriod(request);
        var locationId = request == null ? null : request.locationId();
        if (locationId != null) {
            locationRepository.findByIdAndTenantIdAndDeletedFalse(locationId, user.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        }

        var employees = employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(user.tenantId()).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        var locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(user.tenantId()).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
        var timesheets = timesheetRepository.findByTenantIdAndWeekStartDateBetweenAndStatusInAndDeletedFalseOrderByWeekStartDateAscEmployeeNameAsc(
                        user.tenantId(),
                        period.start(),
                        period.end(),
                        PAYROLL_STATUSES
                ).stream()
                .filter(timesheet -> {
                    var employee = employees.get(timesheet.getEmployeeId());
                    return employee != null && (locationId == null || locationId.equals(employee.getLocationId()));
                })
                .toList();

        var entries = timesheets.isEmpty()
                ? List.<TimeEntry>of()
                : timeEntryRepository.findByTimesheetIdInAndDeletedFalseOrderByClockInAtAsc(timesheets.stream().map(Timesheet::getId).toList());
        var breaks = entries.isEmpty()
                ? List.<TimeBreak>of()
                : timeBreakRepository.findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(entries.stream().map(TimeEntry::getId).toList());
        var entriesByTimesheet = entries.stream().collect(Collectors.groupingBy(TimeEntry::getTimesheetId));
        var breaksByEntry = breaks.stream().collect(Collectors.groupingBy(TimeBreak::getTimeEntryId));

        var blockingIssues = blockingIssues(timesheets, entriesByTimesheet, breaksByEntry);
        if (!blockingIssues.isEmpty()) {
            throw new BadRequestException("Payroll preview blocked: " + String.join("; ", blockingIssues));
        }

        var rules = payRuleConfigRepository.findByTenantIdAndDeletedFalseOrderByEffectiveStartDateDesc(user.tenantId());
        var holidays = payrollHolidayRepository.findByTenantIdAndHolidayDateBetweenAndDeletedFalseOrderByHolidayDateAsc(user.tenantId(), period.start(), period.end());
        var leaveHoursByEmployee = leaveRequestRepository.findByTenantIdAndStatusAndLeaveTypeAndEndDateGreaterThanEqualAndStartDateLessThanEqualAndDeletedFalse(
                        user.tenantId(),
                        "APPROVED",
                        "UNPAID",
                        period.start(),
                        period.end()
                ).stream()
                .collect(Collectors.groupingBy(
                        leave -> leave.getEmployeeId(),
                        Collectors.reducing(BigDecimal.ZERO, leave -> leave.getHours().setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
                ));

        var accumulators = new HashMap<UUID, EmployeePayrollAccumulator>();
        for (var timesheet : timesheets) {
            var employee = employees.get(timesheet.getEmployeeId());
            var accumulator = accumulators.computeIfAbsent(employee.getId(), id -> new EmployeePayrollAccumulator(employee, locations.get(employee.getLocationId())));
            var rule = ruleFor(employee.getLocationId(), timesheet.getWeekStartDate(), rules);
            var weekly = calculateWeekly(timesheet, entriesByTimesheet.getOrDefault(timesheet.getId(), List.of()), breaksByEntry, holidays, rule, employee);
            accumulator.add(weekly);
        }

        leaveHoursByEmployee.forEach((employeeId, hours) -> {
            var accumulator = accumulators.get(employeeId);
            if (accumulator != null) {
                accumulator.unpaidLeaveHours = hours(hours);
            }
        });

        var linesToPersist = accumulators.values().stream()
                .sorted(Comparator.comparing(acc -> acc.employee.getLastName() + acc.employee.getFirstName()))
                .map(EmployeePayrollAccumulator::toLineWithoutPreview)
                .toList();
        var summary = summarize(linesToPersist);
        var explanation = "Generated gross-pay preview for %s to %s from %d submitted/approved timesheets. %s regular hours, %s overtime hours, %s unpaid break hours deducted, %s holiday hours produced $%s premium, and %s approved unpaid leave hours were excluded. Gross pay is $%s."
                .formatted(
                        period.start(),
                        period.end(),
                        timesheets.size(),
                        hours(summary.regularHours),
                        hours(summary.overtimeHours),
                        hours(summary.unpaidBreakHours),
                        hours(summary.holidayHours),
                        money(summary.holidayPremiumPay).toPlainString(),
                        hours(summary.unpaidLeaveHours),
                        money(summary.grossPay).toPlainString()
                );
        var preview = PayrollPreview.builder()
                .tenantId(user.tenantId())
                .periodStart(period.start())
                .periodEnd(period.end())
                .locationId(locationId)
                .regularHours(summary.regularHours)
                .overtimeHours(summary.overtimeHours)
                .unpaidBreakHours(summary.unpaidBreakHours)
                .unpaidLeaveHours(summary.unpaidLeaveHours)
                .holidayHours(summary.holidayHours)
                .holidayPremiumPay(summary.holidayPremiumPay)
                .grossPay(summary.grossPay)
                .employeeCount(linesToPersist.size())
                .timesheetCount(timesheets.size())
                .status("GENERATED")
                .explanation(explanation)
                .generatedByUserId(user.userId())
                .metadata("{\"source\":\"payroll-preview-engine\",\"periodStart\":\"%s\",\"periodEnd\":\"%s\",\"locationId\":%s}"
                        .formatted(period.start(), period.end(), locationId == null ? "null" : "\"" + locationId + "\""))
                .build();
        var savedPreview = payrollPreviewRepository.save(preview);
        var savedLines = linesToPersist.stream()
                .peek(line -> line.setPayrollPreviewId(savedPreview.getId()))
                .map(payrollPreviewLineRepository::save)
                .map(PayrollPreviewLineDto::from)
                .toList();

        auditService.record(
                user,
                "payroll.preview.generated",
                "PayrollPreview",
                savedPreview.getId(),
                null,
                "{\"grossPay\":%s,\"employeeCount\":%d,\"timesheetCount\":%d}".formatted(money(savedPreview.getGrossPay()).toPlainString(), savedPreview.getEmployeeCount(), savedPreview.getTimesheetCount()),
                "{\"periodStart\":\"%s\",\"periodEnd\":\"%s\",\"locationId\":%s}".formatted(period.start(), period.end(), locationId == null ? "null" : "\"" + locationId + "\"")
        );
        webhookEventService.emit(user.tenantId(), "payroll.preview.generated", "PayrollPreview", savedPreview.getId(), payrollPayload(savedPreview));
        return new PayrollPreviewDetailDto(PayrollPreviewDto.from(savedPreview), savedLines);
    }

    private Map<String, Object> payrollPayload(PayrollPreview preview) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("payrollPreviewId", preview.getId());
        payload.put("periodStart", preview.getPeriodStart());
        payload.put("periodEnd", preview.getPeriodEnd());
        payload.put("locationId", preview.getLocationId());
        payload.put("employeeCount", preview.getEmployeeCount());
        payload.put("timesheetCount", preview.getTimesheetCount());
        payload.put("regularHours", preview.getRegularHours());
        payload.put("overtimeHours", preview.getOvertimeHours());
        payload.put("grossPay", preview.getGrossPay());
        payload.put("status", preview.getStatus());
        return payload;
    }

    private WeeklyCalculation calculateWeekly(
            Timesheet timesheet,
            List<TimeEntry> entries,
            Map<UUID, List<TimeBreak>> breaksByEntry,
            List<PayrollHoliday> holidays,
            PayRuleConfig rule,
            Employee employee
    ) {
        long grossMinutes = 0;
        long unpaidBreakMinutes = 0;
        long holidayMinutes = 0;
        for (var entry : entries) {
            var entryGross = Duration.between(entry.getClockInAt(), entry.getClockOutAt()).toMinutes();
            var entryBreakMinutes = rule.isUnpaidBreaksDeductible()
                    ? breaksByEntry.getOrDefault(entry.getId(), List.of()).stream()
                    .filter(timeBreak -> timeBreak.getDurationMinutes() != null)
                    .mapToLong(TimeBreak::getDurationMinutes)
                    .sum()
                    : 0L;
            var entryPaidMinutes = Math.max(0, entryGross - entryBreakMinutes);
            grossMinutes += entryGross;
            unpaidBreakMinutes += entryBreakMinutes;
            if (isHoliday(entry.getEntryDate(), employee.getLocationId(), holidays)) {
                holidayMinutes += entryPaidMinutes;
            }
        }
        var calculation = calculator.calculate(new PayrollPreviewCalculator.PayrollInput(
                timesheet.getEmployeeName(),
                hours(grossMinutes),
                hours(unpaidBreakMinutes),
                BigDecimal.ZERO.setScale(2),
                hours(holidayMinutes),
                employee.getHourlyRate(),
                rule.getWeeklyRegularHours(),
                rule.getOvertimeMultiplier(),
                rule.getHolidayPremiumMultiplier(),
                rule.getName()
        ));
        return new WeeklyCalculation(rule.getName(), calculation);
    }

    private List<String> blockingIssues(List<Timesheet> timesheets, Map<UUID, List<TimeEntry>> entriesByTimesheet, Map<UUID, List<TimeBreak>> breaksByEntry) {
        var issues = new ArrayList<String>();
        for (var timesheet : timesheets) {
            var entries = entriesByTimesheet.getOrDefault(timesheet.getId(), List.of());
            if (entries.isEmpty()) {
                issues.add(timesheet.getEmployeeName() + " has no time entries for week " + timesheet.getWeekStartDate());
                continue;
            }
            for (var entry : entries) {
                if (entry.getStatus() == TimeEntryStatus.OPEN || entry.getStatus() == TimeEntryStatus.MISSED_PUNCH || entry.getClockOutAt() == null) {
                    issues.add("Missed punch for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                    continue;
                }
                if (!entry.getClockOutAt().isAfter(entry.getClockInAt())) {
                    issues.add("Invalid time range for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                    continue;
                }
                var entryBreaks = breaksByEntry.getOrDefault(entry.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(TimeBreak::getBreakStartAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                for (var timeBreak : entryBreaks) {
                    if (timeBreak.getBreakStartAt() == null || timeBreak.getBreakEndAt() == null || timeBreak.getDurationMinutes() == null || timeBreak.getDurationMinutes() <= 0) {
                        issues.add("Invalid break for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                        continue;
                    }
                    if (!timeBreak.getBreakEndAt().isAfter(timeBreak.getBreakStartAt())) {
                        issues.add("Invalid break range for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                    }
                    if (timeBreak.getBreakStartAt().isBefore(entry.getClockInAt()) || timeBreak.getBreakEndAt().isAfter(entry.getClockOutAt())) {
                        issues.add("Break outside shift for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                    }
                }
                for (var i = 0; i < entryBreaks.size() - 1; i++) {
                    if (entryBreaks.get(i).getBreakEndAt() != null && entryBreaks.get(i + 1).getBreakStartAt() != null
                            && entryBreaks.get(i).getBreakEndAt().isAfter(entryBreaks.get(i + 1).getBreakStartAt())) {
                        issues.add("Overlapping breaks for " + entry.getEmployeeName() + " on " + entry.getEntryDate());
                    }
                }
            }
        }
        return issues.stream().distinct().toList();
    }

    private PayRuleConfig ruleFor(UUID locationId, LocalDate date, List<PayRuleConfig> rules) {
        return rules.stream()
                .filter(rule -> Objects.equals(rule.getLocationId(), locationId))
                .filter(rule -> isActive(rule, date))
                .findFirst()
                .or(() -> rules.stream()
                        .filter(rule -> rule.getLocationId() == null)
                        .filter(rule -> isActive(rule, date))
                        .findFirst())
                .orElseGet(this::fallbackRule);
    }

    private boolean isActive(PayRuleConfig rule, LocalDate date) {
        return !rule.getEffectiveStartDate().isAfter(date)
                && (rule.getEffectiveEndDate() == null || !rule.getEffectiveEndDate().isBefore(date));
    }

    private boolean isHoliday(LocalDate date, UUID locationId, List<PayrollHoliday> holidays) {
        return holidays.stream()
                .anyMatch(holiday -> holiday.getHolidayDate().equals(date)
                        && (holiday.getLocationId() == null || Objects.equals(holiday.getLocationId(), locationId)));
    }

    private PayRuleConfig fallbackRule() {
        return PayRuleConfig.builder()
                .name("Tenant default fallback")
                .weeklyRegularHours(new BigDecimal("40.00"))
                .overtimeMultiplier(new BigDecimal("1.50"))
                .holidayPremiumMultiplier(new BigDecimal("1.50"))
                .unpaidBreaksDeductible(true)
                .effectiveStartDate(LocalDate.of(2020, 1, 1))
                .build();
    }

    private PreviewPeriod resolvePeriod(PayrollPreviewRequest request) {
        var defaultStart = LocalDate.now(DEMO_ZONE).with(DayOfWeek.MONDAY).minusWeeks(1);
        var start = request == null || request.periodStart() == null ? defaultStart : request.periodStart();
        var end = request == null || request.periodEnd() == null ? start.plusDays(6) : request.periodEnd();
        if (end.isBefore(start)) {
            throw new BadRequestException("Payroll period end must be on or after start");
        }
        return new PreviewPeriod(start, end);
    }

    private PreviewSummary summarize(List<PayrollPreviewLine> lines) {
        var summary = new PreviewSummary();
        lines.forEach(line -> {
            summary.regularHours = summary.regularHours.add(line.getRegularHours());
            summary.overtimeHours = summary.overtimeHours.add(line.getOvertimeHours());
            summary.unpaidBreakHours = summary.unpaidBreakHours.add(line.getUnpaidBreakHours());
            summary.unpaidLeaveHours = summary.unpaidLeaveHours.add(line.getUnpaidLeaveHours());
            summary.holidayHours = summary.holidayHours.add(line.getHolidayHours());
            summary.holidayPremiumPay = summary.holidayPremiumPay.add(line.getHolidayPremiumPay());
            summary.grossPay = summary.grossPay.add(line.getGrossPay());
        });
        summary.scale();
        return summary;
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record PreviewPeriod(LocalDate start, LocalDate end) {
    }

    private record WeeklyCalculation(String ruleName, PayrollPreviewCalculator.PayrollCalculation calculation) {
    }

    private static class EmployeePayrollAccumulator {
        private final Employee employee;
        private final String locationName;
        private final Set<String> ruleNames = new HashSet<>();
        private int timesheetCount;
        private BigDecimal regularHours = BigDecimal.ZERO;
        private BigDecimal overtimeHours = BigDecimal.ZERO;
        private BigDecimal unpaidBreakHours = BigDecimal.ZERO;
        private BigDecimal unpaidLeaveHours = BigDecimal.ZERO;
        private BigDecimal holidayHours = BigDecimal.ZERO;
        private BigDecimal regularPay = BigDecimal.ZERO;
        private BigDecimal overtimePay = BigDecimal.ZERO;
        private BigDecimal holidayPremiumPay = BigDecimal.ZERO;
        private BigDecimal grossPay = BigDecimal.ZERO;

        private EmployeePayrollAccumulator(Employee employee, String locationName) {
            this.employee = employee;
            this.locationName = locationName;
        }

        private void add(WeeklyCalculation weekly) {
            timesheetCount++;
            ruleNames.add(weekly.ruleName());
            var calculation = weekly.calculation();
            regularHours = regularHours.add(calculation.regularHours());
            overtimeHours = overtimeHours.add(calculation.overtimeHours());
            unpaidBreakHours = unpaidBreakHours.add(calculation.unpaidBreakHours());
            holidayHours = holidayHours.add(calculation.holidayHours());
            regularPay = regularPay.add(calculation.regularPay());
            overtimePay = overtimePay.add(calculation.overtimePay());
            holidayPremiumPay = holidayPremiumPay.add(calculation.holidayPremiumPay());
            grossPay = grossPay.add(calculation.grossPay());
        }

        private PayrollPreviewLine toLineWithoutPreview() {
            var ruleLabel = ruleNames.isEmpty() ? "Tenant default fallback" : String.join(", ", ruleNames);
            var explanation = "%s: %s regular hours and %s overtime hours across %d submitted/approved timesheet(s) at $%s/hr. %s unpaid break hours were deducted, %s holiday worked hours added $%s premium, and %s approved unpaid leave hours were excluded from gross pay. Rule source: %s."
                    .formatted(
                            employee.getFirstName() + " " + employee.getLastName(),
                            PayrollPreviewService.hours(regularHours).stripTrailingZeros().toPlainString(),
                            PayrollPreviewService.hours(overtimeHours).stripTrailingZeros().toPlainString(),
                            timesheetCount,
                            PayrollPreviewService.money(employee.getHourlyRate()).toPlainString(),
                            PayrollPreviewService.hours(unpaidBreakHours).stripTrailingZeros().toPlainString(),
                            PayrollPreviewService.hours(holidayHours).stripTrailingZeros().toPlainString(),
                            PayrollPreviewService.money(holidayPremiumPay).toPlainString(),
                            PayrollPreviewService.hours(unpaidLeaveHours).stripTrailingZeros().toPlainString(),
                            ruleLabel
                    );
            return PayrollPreviewLine.builder()
                    .tenantId(employee.getTenantId())
                    .employeeId(employee.getId())
                    .employeeName(employee.getFirstName() + " " + employee.getLastName())
                    .locationId(employee.getLocationId())
                    .locationName(locationName)
                    .timesheetCount(timesheetCount)
                    .hourlyRate(PayrollPreviewService.money(employee.getHourlyRate()))
                    .regularHours(PayrollPreviewService.hours(regularHours))
                    .overtimeHours(PayrollPreviewService.hours(overtimeHours))
                    .holidayHours(PayrollPreviewService.hours(holidayHours))
                    .unpaidBreakHours(PayrollPreviewService.hours(unpaidBreakHours))
                    .unpaidLeaveHours(PayrollPreviewService.hours(unpaidLeaveHours))
                    .regularPay(PayrollPreviewService.money(regularPay))
                    .overtimePay(PayrollPreviewService.money(overtimePay))
                    .holidayPremiumPay(PayrollPreviewService.money(holidayPremiumPay))
                    .grossPay(PayrollPreviewService.money(grossPay))
                    .ruleName(ruleLabel)
                    .explanation(explanation)
                    .build();
        }
    }

    private static class PreviewSummary {
        private BigDecimal regularHours = BigDecimal.ZERO;
        private BigDecimal overtimeHours = BigDecimal.ZERO;
        private BigDecimal unpaidBreakHours = BigDecimal.ZERO;
        private BigDecimal unpaidLeaveHours = BigDecimal.ZERO;
        private BigDecimal holidayHours = BigDecimal.ZERO;
        private BigDecimal holidayPremiumPay = BigDecimal.ZERO;
        private BigDecimal grossPay = BigDecimal.ZERO;

        private void scale() {
            regularHours = hours(regularHours);
            overtimeHours = hours(overtimeHours);
            unpaidBreakHours = hours(unpaidBreakHours);
            unpaidLeaveHours = hours(unpaidLeaveHours);
            holidayHours = hours(holidayHours);
            holidayPremiumPay = money(holidayPremiumPay);
            grossPay = money(grossPay);
        }
    }
}
