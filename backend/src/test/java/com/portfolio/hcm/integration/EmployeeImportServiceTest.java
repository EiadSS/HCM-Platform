package com.portfolio.hcm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.employee.Employee;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import com.portfolio.hcm.org.Department;
import com.portfolio.hcm.org.DepartmentRepository;
import com.portfolio.hcm.org.JobTitle;
import com.portfolio.hcm.org.JobTitleRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.portfolio.hcm.integration.IntegrationDtos.EmployeeImportPreviewRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeImportServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private EmployeeImportRowRepository importRowRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private JobTitleRepository jobTitleRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;

    private EmployeeImportService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeImportService(
                importJobRepository,
                importRowRepository,
                employeeRepository,
                departmentRepository,
                locationRepository,
                jobTitleRepository,
                new ImportValidationService(),
                currentUserService,
                auditService,
                new ObjectMapper()
        );
    }

    @Test
    void csvParserHandlesQuotedCommaFields() {
        var csv = EmployeeImportService.parseCsv("Employee ID,First Name,Last Name\nNS-020,\"Ava, Marie\",Lopez\n");

        assertThat(csv.headers()).containsExactly("Employee ID", "First Name", "Last Name");
        assertThat(csv.rows()).hasSize(1);
        assertThat(csv.rows().get(0).values().get("First Name")).isEqualTo("Ava, Marie");
    }

    @Test
    void previewMapsFieldsAndPersistsPerRowErrors() {
        stubUser();
        stubReferenceData();
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(importRowRepository.save(any(EmployeeImportRow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var csv = """
                Worker ID,Given,Surname,Email Address,State,Type,Team,Site,Role,Manager,Rate,Cap,Hire
                NS-020,Ava,Lopez,ava.lopez@example.com,ACTIVE,PART_TIME,Retail,Downtown Store,Retail Associate,manager@example.com,23.00,24,2026-06-01
                NS-001,Bad,Conflict,existing@example.com,STARTED,PART_TIME,Unknown,Downtown Store,Retail Associate,missing@example.com,-1,0,bad-date
                """;

        var result = service.preview(new EmployeeImportPreviewRequest("mapped.csv", csv, mapping()));

        assertThat(result.job().status()).isEqualTo("PREVIEW_READY");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows()).extracting("status").contains("VALID", "ERROR");
        assertThat(result.rows().get(1).errors()).extracting("field")
                .contains("employeeNumber", "workEmail", "department", "managerEmail", "status", "hourlyRate", "weeklyHourCap", "hireDate");
        verify(auditService).record(any(AuthenticatedUser.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commitCreatesEmployeesForValidUncommittedRowsOnly() {
        var jobId = UUID.randomUUID();
        var job = ImportJob.builder()
                .tenantId(TENANT_ID)
                .fileName("preview.csv")
                .status("PREVIEW_READY")
                .totalRows(2)
                .successRows(1)
                .errorRows(1)
                .committedRows(0)
                .summary("Preview ready")
                .fieldMappingJson("{}")
                .sourceMetadata("{}")
                .build();
        job.setId(jobId);
        var valid = importRow(jobId, 2, "VALID", "{\"employeeNumber\":\"NS-020\",\"firstName\":\"Ava\",\"lastName\":\"Lopez\",\"workEmail\":\"ava.lopez@example.com\",\"status\":\"ACTIVE\",\"employmentType\":\"PART_TIME\",\"department\":\"Retail\",\"location\":\"Downtown Store\",\"jobTitle\":\"Retail Associate\",\"managerEmail\":\"manager@example.com\",\"hourlyRate\":\"23.00\",\"weeklyHourCap\":\"24\",\"hireDate\":\"2026-06-01\"}", "[]");
        var error = importRow(jobId, 3, "ERROR", "{\"employeeNumber\":\"NS-001\"}", "[{\"rowNumber\":3,\"field\":\"employeeNumber\",\"message\":\"Employee ID conflicts with an existing employee\"}]");

        stubUser();
        stubReferenceData();
        when(importJobRepository.findByIdAndTenantIdAndDeletedFalse(jobId, TENANT_ID)).thenReturn(Optional.of(job));
        when(importRowRepository.findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(TENANT_ID, jobId)).thenReturn(List.of(valid, error));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(importRowRepository.save(any(EmployeeImportRow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.commit(jobId);

        assertThat(result.job().status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.job().committedRows()).isEqualTo(1);
        assertThat(valid.getStatus()).isEqualTo("IMPORTED");
        assertThat(error.getStatus()).isEqualTo("ERROR");
        verify(employeeRepository).save(any(Employee.class));
    }

    private void stubUser() {
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(USER_ID, TENANT_ID, "hr@example.com", Set.of(UserRole.HR_ADMIN)));
    }

    private void stubReferenceData() {
        var manager = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-001")
                .firstName("Maya")
                .lastName("Thompson")
                .workEmail("manager@example.com")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(EmploymentType.FULL_TIME)
                .hourlyRate(new BigDecimal("42.00"))
                .weeklyHourCap(new BigDecimal("40"))
                .hireDate(LocalDate.of(2020, 1, 1))
                .build();
        manager.setId(UUID.randomUUID());
        var existing = Employee.builder()
                .tenantId(TENANT_ID)
                .employeeNumber("NS-001")
                .firstName("Existing")
                .lastName("Employee")
                .workEmail("existing@example.com")
                .status(EmployeeStatus.ACTIVE)
                .employmentType(EmploymentType.FULL_TIME)
                .hourlyRate(new BigDecimal("30.00"))
                .weeklyHourCap(new BigDecimal("40"))
                .hireDate(LocalDate.of(2020, 1, 1))
                .build();
        existing.setId(UUID.randomUUID());
        var department = Department.builder().tenantId(TENANT_ID).name("Retail").costCenter("RET").build();
        department.setId(UUID.randomUUID());
        var location = Location.builder().tenantId(TENANT_ID).name("Downtown Store").timezone("America/Toronto").region("Ontario").build();
        location.setId(UUID.randomUUID());
        var title = JobTitle.builder().tenantId(TENANT_ID).name("Retail Associate").careerLevel("Hourly").build();
        title.setId(UUID.randomUUID());
        when(employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(TENANT_ID)).thenReturn(List.of(manager, existing));
        when(departmentRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(department));
        when(locationRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(location));
        when(jobTitleRepository.findByTenantIdAndDeletedFalseOrderByName(TENANT_ID)).thenReturn(List.of(title));
    }

    private Map<String, String> mapping() {
        return Map.ofEntries(
                Map.entry("employeeNumber", "Worker ID"),
                Map.entry("firstName", "Given"),
                Map.entry("lastName", "Surname"),
                Map.entry("workEmail", "Email Address"),
                Map.entry("status", "State"),
                Map.entry("employmentType", "Type"),
                Map.entry("department", "Team"),
                Map.entry("location", "Site"),
                Map.entry("jobTitle", "Role"),
                Map.entry("managerEmail", "Manager"),
                Map.entry("hourlyRate", "Rate"),
                Map.entry("weeklyHourCap", "Cap"),
                Map.entry("hireDate", "Hire")
        );
    }

    private EmployeeImportRow importRow(UUID jobId, int rowNumber, String status, String mappedJson, String errorJson) {
        var row = EmployeeImportRow.builder()
                .tenantId(TENANT_ID)
                .importJobId(jobId)
                .rowNumber(rowNumber)
                .rawJson(mappedJson)
                .mappedJson(mappedJson)
                .status(status)
                .errorJson(errorJson)
                .build();
        row.setId(UUID.randomUUID());
        return row;
    }
}
