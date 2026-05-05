package com.portfolio.hcm.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.integration.IntegrationDtos.EmployeeImportPreviewRequest;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDto;
import static com.portfolio.hcm.integration.IntegrationDtos.TimesheetExportRowDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDto;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    private final EmployeeImportService employeeImportService;
    private final TimesheetExportService timesheetExportService;
    private final WebhookEventService webhookEventService;

    public IntegrationController(EmployeeImportService employeeImportService, TimesheetExportService timesheetExportService, WebhookEventService webhookEventService) {
        this.employeeImportService = employeeImportService;
        this.timesheetExportService = timesheetExportService;
        this.webhookEventService = webhookEventService;
    }

    @GetMapping("/imports/employees")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public List<ImportJobDto> imports() {
        return employeeImportService.list();
    }

    @PostMapping("/imports/employees/preview")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ImportJobDetailDto previewEmployeeImport(@RequestBody EmployeeImportPreviewRequest request) {
        return employeeImportService.preview(request);
    }

    @GetMapping("/imports/employees/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ImportJobDetailDto employeeImportDetail(@PathVariable UUID id) {
        return employeeImportService.detail(id);
    }

    @PostMapping("/imports/employees/{id}/commit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ImportJobDetailDto commitEmployeeImport(@PathVariable UUID id) {
        return employeeImportService.commit(id);
    }

    @GetMapping("/imports/employees/{id}/errors.csv")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<String> employeeImportErrors(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employee-import-errors.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(employeeImportService.errorReport(id));
    }

    @GetMapping("/exports/timesheets.csv")
    @PreAuthorize("hasAnyRole('PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<String> exportTimesheetsCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=approved-timesheets-demo.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(timesheetExportService.approvedCsv());
    }

    @GetMapping("/exports/timesheets.json")
    @PreAuthorize("hasAnyRole('PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<List<TimesheetExportRowDto>> exportTimesheetsJson() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=approved-timesheets-demo.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(timesheetExportService.approvedRows());
    }

    @GetMapping("/webhooks/events")
    @PreAuthorize("hasAnyRole('HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public List<WebhookEventDto> webhookEvents() {
        return webhookEventService.list();
    }

    @GetMapping("/webhooks/events/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public WebhookEventDetailDto webhookEventDetail(@PathVariable UUID id) {
        return webhookEventService.detail(id);
    }

    @PostMapping("/webhooks/events/{id}/redeliver")
    @PreAuthorize("hasAnyRole('HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public WebhookEventDetailDto redeliverWebhookEvent(@PathVariable UUID id) {
        return webhookEventService.redeliver(id);
    }
}
