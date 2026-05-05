package com.portfolio.hcm.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.hcm.audit.AuditService;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
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
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.portfolio.hcm.integration.ImportValidationService.EmployeeImportRecord;
import static com.portfolio.hcm.integration.ImportValidationService.RowValidationError;
import static com.portfolio.hcm.integration.ImportValidationService.ValidationContext;
import static com.portfolio.hcm.integration.ImportValidationService.normalize;
import static com.portfolio.hcm.integration.IntegrationDtos.EmployeeImportPreviewRequest;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportRowDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportRowErrorDto;

@Service
public class EmployeeImportService {
    static final List<String> CANONICAL_FIELDS = List.of(
            "employeeNumber",
            "firstName",
            "lastName",
            "workEmail",
            "status",
            "employmentType",
            "department",
            "location",
            "jobTitle",
            "managerEmail",
            "hourlyRate",
            "weeklyHourCap",
            "hireDate"
    );

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportRowErrorDto>> ERROR_LIST = new TypeReference<>() {
    };

    private final ImportJobRepository importJobRepository;
    private final EmployeeImportRowRepository importRowRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final JobTitleRepository jobTitleRepository;
    private final ImportValidationService validationService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EmployeeImportService(
            ImportJobRepository importJobRepository,
            EmployeeImportRowRepository importRowRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            JobTitleRepository jobTitleRepository,
            ImportValidationService validationService,
            CurrentUserService currentUserService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.importJobRepository = importJobRepository;
        this.importRowRepository = importRowRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.validationService = validationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportJobDto> list() {
        return importJobRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(currentUserService.tenantId()).stream()
                .map(ImportJobDto::from)
                .toList();
    }

    @Transactional
    public ImportJobDetailDto preview(EmployeeImportPreviewRequest request) {
        var user = currentUserService.requireUser();
        if (request == null || request.csvContent() == null || request.csvContent().isBlank()) {
            throw new BadRequestException("CSV content is required");
        }
        var queuedAt = Instant.now();
        var job = importJobRepository.save(ImportJob.builder()
                .tenantId(user.tenantId())
                .fileName(request.fileName() == null || request.fileName().isBlank() ? "employee-import.csv" : request.fileName())
                .status("QUEUED")
                .totalRows(0)
                .successRows(0)
                .errorRows(0)
                .committedRows(0)
                .summary("Employee import queued for preview.")
                .queuedAt(queuedAt)
                .sourceMetadata("{}")
                .build());

        try {
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            importJobRepository.save(job);

            var csv = parseCsv(request.csvContent());
            if (csv.headers().isEmpty()) {
                throw new BadRequestException("CSV header row is required");
            }
            var mapping = resolveMapping(csv.headers(), request.fieldMapping());
            var reference = referenceData(user.tenantId());
            var mappedRecords = csv.rows().stream()
                    .map(raw -> mappedRecord(raw.rowNumber(), mapRow(raw.values(), mapping)))
                    .toList();
            var errors = validationService.validate(mappedRecords, reference.validationContext());
            var errorsByRow = errors.stream().collect(Collectors.groupingBy(RowValidationError::rowNumber));

            var persistedRows = new ArrayList<EmployeeImportRow>();
            for (var raw : csv.rows()) {
                var mapped = mapRow(raw.values(), mapping);
                var rowErrors = errorsByRow.getOrDefault(raw.rowNumber(), List.of()).stream()
                        .map(error -> new ImportRowErrorDto(error.rowNumber(), error.field(), error.message()))
                        .toList();
                persistedRows.add(importRowRepository.save(EmployeeImportRow.builder()
                        .tenantId(user.tenantId())
                        .importJobId(job.getId())
                        .rowNumber(raw.rowNumber())
                        .rawJson(writeJson(raw.values()))
                        .mappedJson(writeJson(mapped))
                        .status(rowErrors.isEmpty() ? "VALID" : "ERROR")
                        .errorJson(writeJson(rowErrors))
                        .build()));
            }

            var errorReport = errorReportCsv(errors.stream()
                    .map(error -> new ImportRowErrorDto(error.rowNumber(), error.field(), error.message()))
                    .toList());
            job.setStatus("PREVIEW_READY");
            job.setTotalRows(csv.rows().size());
            job.setSuccessRows((int) persistedRows.stream().filter(row -> "VALID".equals(row.getStatus())).count());
            job.setErrorRows(errorsByRow.size());
            job.setFieldMappingJson(writeJson(mapping));
            job.setSourceMetadata(writeJson(Map.of("detectedHeaders", String.join("|", csv.headers()))));
            job.setErrorReportCsv(errorReport);
            job.setPreviewedAt(Instant.now());
            job.setSummary("%d row(s) ready to import. %d row(s) need correction.".formatted(job.getSuccessRows(), job.getErrorRows()));
            var saved = importJobRepository.save(job);
            auditService.record(user, "employee.import.previewed", "ImportJob", saved.getId(), null, snapshot(saved), "{\"source\":\"integration-center\"}");
            return detailFor(saved, persistedRows);
        } catch (BadRequestException ex) {
            failJob(job, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            failJob(job, "Employee import preview failed");
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ImportJobDetailDto detail(UUID id) {
        var tenantId = currentUserService.tenantId();
        var job = importJobRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found"));
        return detailFor(job, importRowRepository.findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(tenantId, id));
    }

    @Transactional
    public ImportJobDetailDto commit(UUID id) {
        var user = currentUserService.requireUser();
        var job = importJobRepository.findByIdAndTenantIdAndDeletedFalse(id, user.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found"));
        if (!"PREVIEW_READY".equals(job.getStatus()) && !"COMPLETED_WITH_ERRORS".equals(job.getStatus())) {
            throw new BadRequestException("Only preview-ready imports can be committed");
        }
        var rows = importRowRepository.findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(user.tenantId(), job.getId());
        var reference = referenceData(user.tenantId());
        job.setStatus("COMMITTING");
        job.setCommittedAt(Instant.now());
        importJobRepository.save(job);

        var committed = 0;
        for (var row : rows) {
            if (!"VALID".equals(row.getStatus())) {
                continue;
            }
            var mapped = readStringMap(row.getMappedJson());
            var employee = employeeFrom(mapped, user.tenantId(), reference);
            var saved = employeeRepository.save(employee);
            row.setStatus("IMPORTED");
            row.setImportedEmployeeId(saved.getId());
            importRowRepository.save(row);
            committed++;
        }
        job.setCommittedRows(job.getCommittedRows() + committed);
        job.setSuccessRows(job.getCommittedRows());
        job.setStatus(job.getErrorRows() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        job.setCompletedAt(Instant.now());
        job.setSummary("%d employee row(s) imported. %d row(s) retained with validation errors.".formatted(job.getCommittedRows(), job.getErrorRows()));
        var savedJob = importJobRepository.save(job);
        auditService.record(user, "employee.import.committed", "ImportJob", savedJob.getId(), null, snapshot(savedJob), "{\"committedRows\":%d}".formatted(committed));
        return detailFor(savedJob, importRowRepository.findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(user.tenantId(), id));
    }

    @Transactional(readOnly = true)
    public String errorReport(UUID id) {
        var tenantId = currentUserService.tenantId();
        var job = importJobRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found"));
        if (job.getErrorReportCsv() != null && !job.getErrorReportCsv().isBlank()) {
            return job.getErrorReportCsv();
        }
        var errors = importRowRepository.findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(tenantId, id).stream()
                .flatMap(row -> readErrors(row.getErrorJson()).stream())
                .toList();
        return errorReportCsv(errors);
    }

    static ParsedCsv parseCsv(String csvContent) {
        var lines = parseLines(csvContent);
        if (lines.isEmpty()) {
            return new ParsedCsv(List.of(), List.of());
        }
        var headers = lines.get(0).stream().map(String::trim).toList();
        var rows = new ArrayList<RawCsvRow>();
        for (int i = 1; i < lines.size(); i++) {
            var values = new LinkedHashMap<String, String>();
            var line = lines.get(i);
            var blank = true;
            for (int j = 0; j < headers.size(); j++) {
                var value = j < line.size() ? line.get(j).trim() : "";
                values.put(headers.get(j), value);
                blank = blank && value.isBlank();
            }
            if (!blank) {
                rows.add(new RawCsvRow(i + 1, values));
            }
        }
        return new ParsedCsv(headers, rows);
    }

    private static List<List<String>> parseLines(String csvContent) {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var value = new StringBuilder();
        var quoted = false;
        for (int i = 0; i < csvContent.length(); i++) {
            var ch = csvContent.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < csvContent.length() && csvContent.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                row.add(value.toString());
                value.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < csvContent.length() && csvContent.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(value.toString());
                value.setLength(0);
                if (!row.stream().allMatch(String::isBlank)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                value.append(ch);
            }
        }
        row.add(value.toString());
        if (!row.stream().allMatch(String::isBlank)) {
            rows.add(row);
        }
        return rows;
    }

    private Map<String, String> resolveMapping(List<String> headers, Map<String, String> requestedMapping) {
        var mapping = new LinkedHashMap<String, String>();
        var byNormalized = headers.stream().collect(Collectors.toMap(EmployeeImportService::normalizeHeader, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (var field : CANONICAL_FIELDS) {
            var requested = requestedMapping == null ? null : requestedMapping.get(field);
            if (requested != null && headers.contains(requested)) {
                mapping.put(field, requested);
                continue;
            }
            mapping.put(field, byNormalized.getOrDefault(normalizeHeader(field), ""));
        }
        return mapping;
    }

    private Map<String, String> mapRow(Map<String, String> raw, Map<String, String> mapping) {
        var mapped = new LinkedHashMap<String, String>();
        for (var field : CANONICAL_FIELDS) {
            var sourceHeader = mapping.get(field);
            mapped.put(field, sourceHeader == null || sourceHeader.isBlank() ? "" : raw.getOrDefault(sourceHeader, ""));
        }
        return mapped;
    }

    private EmployeeImportRecord mappedRecord(int rowNumber, Map<String, String> mapped) {
        return new EmployeeImportRecord(
                rowNumber,
                mapped.get("employeeNumber"),
                mapped.get("firstName"),
                mapped.get("lastName"),
                mapped.get("workEmail"),
                mapped.get("status"),
                mapped.get("employmentType"),
                mapped.get("department"),
                mapped.get("location"),
                mapped.get("jobTitle"),
                mapped.get("managerEmail"),
                mapped.get("hourlyRate"),
                mapped.get("weeklyHourCap"),
                mapped.get("hireDate")
        );
    }

    private Employee employeeFrom(Map<String, String> mapped, UUID tenantId, ReferenceData reference) {
        return Employee.builder()
                .tenantId(tenantId)
                .employeeNumber(mapped.get("employeeNumber").trim())
                .firstName(mapped.get("firstName").trim())
                .lastName(mapped.get("lastName").trim())
                .workEmail(mapped.get("workEmail").trim())
                .status(EmployeeStatus.valueOf(mapped.get("status").trim().toUpperCase(Locale.ROOT)))
                .employmentType(EmploymentType.valueOf(mapped.get("employmentType").trim().toUpperCase(Locale.ROOT)))
                .departmentId(reference.departmentsByName().get(normalize(mapped.get("department"))).getId())
                .locationId(reference.locationsByName().get(normalize(mapped.get("location"))).getId())
                .jobTitleId(normalize(mapped.get("jobTitle")).isBlank() ? null : reference.jobTitlesByName().get(normalize(mapped.get("jobTitle"))).getId())
                .managerEmployeeId(normalize(mapped.get("managerEmail")).isBlank() ? null : reference.managersByEmail().get(normalize(mapped.get("managerEmail"))).getId())
                .hourlyRate(new BigDecimal(mapped.get("hourlyRate").trim()))
                .weeklyHourCap(new BigDecimal(mapped.get("weeklyHourCap").trim()))
                .hireDate(LocalDate.parse(mapped.get("hireDate").trim()))
                .build();
    }

    private ReferenceData referenceData(UUID tenantId) {
        var employees = employeeRepository.findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(tenantId);
        var departments = departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(department -> normalize(department.getName()), Function.identity()));
        var locations = locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(location -> normalize(location.getName()), Function.identity()));
        var titles = jobTitleRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream()
                .collect(Collectors.toMap(title -> normalize(title.getName()), Function.identity()));
        var managers = employees.stream()
                .collect(Collectors.toMap(employee -> normalize(employee.getWorkEmail()), Function.identity(), (left, right) -> left));
        var context = new ValidationContext(
                employees.stream().map(employee -> normalize(employee.getEmployeeNumber())).collect(Collectors.toSet()),
                employees.stream().map(employee -> normalize(employee.getWorkEmail())).collect(Collectors.toSet()),
                departments.keySet(),
                locations.keySet(),
                titles.keySet(),
                managers.keySet()
        );
        return new ReferenceData(departments, locations, titles, managers, context);
    }

    private ImportJobDetailDto detailFor(ImportJob job, List<EmployeeImportRow> rows) {
        var mapping = readStringMap(job.getFieldMappingJson());
        var detectedHeaders = readStringMap(job.getSourceMetadata()).getOrDefault("detectedHeaders", "").isBlank()
                ? List.<String>of()
                : List.of(readStringMap(job.getSourceMetadata()).get("detectedHeaders").split("\\|"));
        return new ImportJobDetailDto(
                ImportJobDto.from(job),
                detectedHeaders,
                mapping,
                rows.stream().map(row -> new ImportRowDto(
                        row.getId(),
                        row.getRowNumber(),
                        row.getStatus(),
                        readStringMap(row.getRawJson()),
                        readStringMap(row.getMappedJson()),
                        readErrors(row.getErrorJson()),
                        row.getImportedEmployeeId()
                )).toList()
        );
    }

    private void failJob(ImportJob job, String message) {
        job.setStatus("FAILED");
        job.setSummary(message);
        job.setFailedAt(Instant.now());
        job.setCompletedAt(Instant.now());
        importJobRepository.save(job);
    }

    private String errorReportCsv(List<ImportRowErrorDto> errors) {
        var rows = new StringBuilder("row,field,message\n");
        errors.forEach(error -> rows.append("%s,%s,%s%n".formatted(error.rowNumber(), csv(error.field()), csv(error.message()))));
        return rows.toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BadRequestException("Unable to serialize import data");
        }
    }

    private Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (RuntimeException | java.io.IOException ex) {
            return Map.of();
        }
    }

    private List<ImportRowErrorDto> readErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ERROR_LIST);
        } catch (RuntimeException | java.io.IOException ex) {
            return List.of();
        }
    }

    private String snapshot(ImportJob job) {
        return "{\"fileName\":\"%s\",\"status\":\"%s\",\"totalRows\":%d,\"successRows\":%d,\"errorRows\":%d,\"committedRows\":%d}"
                .formatted(job.getFileName(), job.getStatus(), job.getTotalRows(), job.getSuccessRows(), job.getErrorRows(), job.getCommittedRows());
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record ParsedCsv(List<String> headers, List<RawCsvRow> rows) {
    }

    public record RawCsvRow(int rowNumber, Map<String, String> values) {
    }

    private record ReferenceData(
            Map<String, Department> departmentsByName,
            Map<String, Location> locationsByName,
            Map<String, JobTitle> jobTitlesByName,
            Map<String, Employee> managersByEmail,
            ValidationContext validationContext
    ) {
    }
}
