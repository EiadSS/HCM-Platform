package com.portfolio.hcm.employee;

import com.portfolio.hcm.audit.AuditLogDto;
import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.integration.WebhookEventService;
import com.portfolio.hcm.org.DepartmentRepository;
import com.portfolio.hcm.org.JobTitleRepository;
import com.portfolio.hcm.org.LocationRepository;
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.portfolio.hcm.employee.EmployeeDtos.EmployeeRequest;
import static com.portfolio.hcm.employee.EmployeeDtos.EmployeeResponse;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final JobTitleRepository jobTitleRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final WebhookEventService webhookEventService;
    private final CurrentUserService currentUserService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            JobTitleRepository jobTitleRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            WebhookEventService webhookEventService,
            CurrentUserService currentUserService
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.webhookEventService = webhookEventService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public java.util.List<EmployeeResponse> list() {
        var tenantId = currentUserService.tenantId();
        return mapEmployees(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(tenantId));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        var tenantId = currentUserService.tenantId();
        if (employeeRepository.existsByTenantIdAndEmployeeNumberIgnoreCaseAndDeletedFalse(tenantId, request.employeeNumber())) {
            throw new BadRequestException("Employee number already exists in this tenant");
        }
        if (employeeRepository.existsByTenantIdAndWorkEmailIgnoreCaseAndDeletedFalse(tenantId, request.workEmail())) {
            throw new BadRequestException("Work email already exists in this tenant");
        }
        var employee = Employee.builder()
                .tenantId(tenantId)
                .employeeNumber(request.employeeNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .workEmail(request.workEmail())
                .status(request.status())
                .employmentType(request.employmentType())
                .departmentId(request.departmentId())
                .locationId(request.locationId())
                .jobTitleId(request.jobTitleId())
                .managerEmployeeId(request.managerEmployeeId())
                .hourlyRate(request.hourlyRate())
                .weeklyHourCap(request.weeklyHourCap())
                .hireDate(request.hireDate())
                .build();
        var saved = employeeRepository.save(employee);
        auditService.record("employee.created", "Employee", saved.getId(), null, employeeSnapshot(saved), "{\"source\":\"employee-api\"}");
        return mapEmployees(java.util.List.of(saved)).get(0);
    }

    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest request) {
        var tenantId = currentUserService.tenantId();
        var employee = employeeRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        var before = employeeSnapshot(employee);
        employee.setEmployeeNumber(request.employeeNumber());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setWorkEmail(request.workEmail());
        employee.setStatus(request.status());
        employee.setEmploymentType(request.employmentType());
        employee.setDepartmentId(request.departmentId());
        employee.setLocationId(request.locationId());
        employee.setJobTitleId(request.jobTitleId());
        employee.setManagerEmployeeId(request.managerEmployeeId());
        employee.setHourlyRate(request.hourlyRate());
        employee.setWeeklyHourCap(request.weeklyHourCap());
        employee.setHireDate(request.hireDate());
        var saved = employeeRepository.save(employee);
        auditService.record("employee.updated", "Employee", saved.getId(), before, employeeSnapshot(saved), "{\"source\":\"employee-api\"}");
        webhookEventService.emit(tenantId, "employee.updated", "Employee", saved.getId(), employeePayload(saved));
        return mapEmployees(java.util.List.of(saved)).get(0);
    }

    @Transactional
    public EmployeeResponse deactivate(UUID id) {
        var tenantId = currentUserService.tenantId();
        var employee = employeeRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        var before = employeeSnapshot(employee);
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setTerminationDate(LocalDate.now());
        var saved = employeeRepository.save(employee);
        auditService.record("employee.deactivated", "Employee", saved.getId(), before, employeeSnapshot(saved), "{\"softDelete\":true}");
        return mapEmployees(java.util.List.of(saved)).get(0);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLogDto> history(UUID id) {
        var tenantId = currentUserService.tenantId();
        return auditLogRepository.findByTenantIdAndEntityTypeAndEntityIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, "Employee", id)
                .stream()
                .map(AuditLogDto::from)
                .toList();
    }

    private java.util.List<EmployeeResponse> mapEmployees(java.util.List<Employee> employees) {
        var tenantId = currentUserService.tenantId();
        Map<UUID, String> departments = departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(com.portfolio.hcm.org.Department::getId, com.portfolio.hcm.org.Department::getName));
        Map<UUID, String> locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(com.portfolio.hcm.org.Location::getId, com.portfolio.hcm.org.Location::getName));
        Map<UUID, String> titles = jobTitleRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(com.portfolio.hcm.org.JobTitle::getId, com.portfolio.hcm.org.JobTitle::getName));
        Map<UUID, Employee> byId = employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return employees.stream()
                .map(employee -> {
                    var manager = employee.getManagerEmployeeId() == null ? null : byId.get(employee.getManagerEmployeeId());
                    var managerName = manager == null ? null : manager.getFirstName() + " " + manager.getLastName();
                    return new EmployeeResponse(
                            employee.getId(),
                            employee.getEmployeeNumber(),
                            employee.getFirstName(),
                            employee.getLastName(),
                            employee.getFirstName() + " " + employee.getLastName(),
                            employee.getWorkEmail(),
                            employee.getStatus(),
                            employee.getEmploymentType(),
                            employee.getDepartmentId(),
                            departments.get(employee.getDepartmentId()),
                            employee.getLocationId(),
                            locations.get(employee.getLocationId()),
                            employee.getJobTitleId(),
                            titles.get(employee.getJobTitleId()),
                            employee.getManagerEmployeeId(),
                            managerName,
                            employee.getHourlyRate(),
                            employee.getWeeklyHourCap(),
                            employee.getHireDate(),
                            employee.getTerminationDate()
                    );
                })
                .toList();
    }

    private String employeeSnapshot(Employee employee) {
        return """
                {"employeeNumber":"%s","name":"%s %s","workEmail":"%s","status":"%s","employmentType":"%s","hourlyRate":%s,"weeklyHourCap":%s}
                """.formatted(
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getWorkEmail(),
                employee.getStatus(),
                employee.getEmploymentType(),
                employee.getHourlyRate(),
                employee.getWeeklyHourCap()
        ).trim();
    }

    private Map<String, Object> employeePayload(Employee employee) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("employeeId", employee.getId());
        payload.put("employeeNumber", employee.getEmployeeNumber());
        payload.put("fullName", employee.getFirstName() + " " + employee.getLastName());
        payload.put("workEmail", employee.getWorkEmail());
        payload.put("status", employee.getStatus().name());
        payload.put("employmentType", employee.getEmploymentType().name());
        payload.put("departmentId", employee.getDepartmentId());
        payload.put("locationId", employee.getLocationId());
        payload.put("hourlyRate", employee.getHourlyRate());
        payload.put("weeklyHourCap", employee.getWeeklyHourCap());
        return payload;
    }
}
