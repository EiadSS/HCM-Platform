package com.portfolio.hcm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.hcm.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.portfolio.hcm.integration.IntegrationDtos.EmployeeImportPreviewRequest;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportJobDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportRowDto;
import static com.portfolio.hcm.integration.IntegrationDtos.ImportRowErrorDto;
import static com.portfolio.hcm.integration.IntegrationDtos.TimesheetExportRowDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookDeliveryAttemptDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntegrationControllerTest {
    private final EmployeeImportService employeeImportService = mock(EmployeeImportService.class);
    private final TimesheetExportService timesheetExportService = mock(TimesheetExportService.class);
    private final WebhookEventService webhookEventService = mock(WebhookEventService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IntegrationController(employeeImportService, timesheetExportService, webhookEventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void previewEndpointReturnsRowsAndValidationErrors() throws Exception {
        when(employeeImportService.preview(any(EmployeeImportPreviewRequest.class))).thenReturn(importDetail());

        mockMvc.perform(post("/api/v1/integrations/imports/employees/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeImportPreviewRequest("employees.csv", "Employee ID\nNS-020\n", Map.of("employeeNumber", "Employee ID")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("PREVIEW_READY"))
                .andExpect(jsonPath("$.rows[1].errors[0].field").value("department"));

        verify(employeeImportService).preview(any(EmployeeImportPreviewRequest.class));
    }

    @Test
    void errorReportAndExportsReturnDownloadablePayloads() throws Exception {
        when(employeeImportService.errorReport(any(UUID.class))).thenReturn("row,field,message\n3,department,Invalid department\n");
        when(timesheetExportService.approvedCsv()).thenReturn("timesheetId,employeeId,employeeName,weekStartDate,regularHours,overtimeHours,status,approvedAt,lockedPayPeriod,managerNote\n");
        when(timesheetExportService.approvedRows()).thenReturn(List.of(exportRow()));

        mockMvc.perform(get("/api/v1/integrations/imports/employees/{id}/errors.csv", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid department")));

        mockMvc.perform(get("/api/v1/integrations/exports/timesheets.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("timesheetId")));

        mockMvc.perform(get("/api/v1/integrations/exports/timesheets.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void webhookDetailAndRedeliveryEndpointsReturnAttempts() throws Exception {
        var eventId = UUID.randomUUID();
        when(webhookEventService.detail(eventId)).thenReturn(webhookDetail(eventId));
        when(webhookEventService.redeliver(eventId)).thenReturn(webhookDetail(eventId));

        mockMvc.perform(get("/api/v1/integrations/webhooks/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.eventType").value("payroll.preview.generated"))
                .andExpect(jsonPath("$.attempts[0].status").value("FAILED"));

        mockMvc.perform(post("/api/v1/integrations/webhooks/events/{id}/redeliver", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payloadJson").value(org.hamcrest.Matchers.containsString("grossPay")));
    }

    @Test
    void endpointsHaveRoleSpecificPreAuthorizeRules() throws Exception {
        assertThat(IntegrationController.class.getMethod("previewEmployeeImport", EmployeeImportPreviewRequest.class).getAnnotation(PreAuthorize.class).value())
                .contains("HR_ADMIN").contains("SYSTEM_ADMIN");
        assertThat(IntegrationController.class.getMethod("exportTimesheetsCsv").getAnnotation(PreAuthorize.class).value())
                .contains("PAYROLL_ADMIN").contains("SYSTEM_ADMIN");
        assertThat(IntegrationController.class.getMethod("webhookEvents").getAnnotation(PreAuthorize.class).value())
                .contains("HR_ADMIN").contains("PAYROLL_ADMIN").contains("SYSTEM_ADMIN");
    }

    private ImportJobDetailDto importDetail() {
        var job = new ImportJobDto(
                UUID.randomUUID(),
                "employees.csv",
                "PREVIEW_READY",
                2,
                1,
                1,
                0,
                "1 row ready",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
        );
        return new ImportJobDetailDto(
                job,
                List.of("Employee ID", "Department"),
                Map.of("employeeNumber", "Employee ID"),
                List.of(
                        new ImportRowDto(UUID.randomUUID(), 2, "VALID", Map.of("Employee ID", "NS-020"), Map.of("employeeNumber", "NS-020"), List.of(), null),
                        new ImportRowDto(UUID.randomUUID(), 3, "ERROR", Map.of("Employee ID", "NS-021"), Map.of("employeeNumber", "NS-021"), List.of(new ImportRowErrorDto(3, "department", "Invalid department")), null)
                )
        );
    }

    private TimesheetExportRowDto exportRow() {
        return new TimesheetExportRowDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Elena Garcia",
                LocalDate.of(2026, 4, 27),
                new BigDecimal("5.50"),
                new BigDecimal("0.00"),
                "APPROVED",
                Instant.now(),
                true,
                "Approved"
        );
    }

    private WebhookEventDetailDto webhookDetail(UUID eventId) {
        var attempt = new WebhookDeliveryAttemptDto(UUID.randomUUID(), "Northstar Demo Receiver", "https://example.test", "FAILED", 503, "Unavailable", Instant.now());
        return new WebhookEventDetailDto(
                new WebhookEventDto(eventId, "payroll.preview.generated", "PayrollPreview", UUID.randomUUID(), "FAILED", Instant.now(), attempt),
                "{\"grossPay\":1740.42}",
                List.of(attempt)
        );
    }
}
