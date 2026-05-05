package com.portfolio.hcm.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IntegrationDtos {
    private IntegrationDtos() {
    }

    public record ImportJobDto(
            UUID id,
            String fileName,
            String status,
            int totalRows,
            int successRows,
            int errorRows,
            int committedRows,
            String summary,
            Instant queuedAt,
            Instant startedAt,
            Instant previewedAt,
            Instant committedAt,
            Instant completedAt,
            Instant failedAt
    ) {
        static ImportJobDto from(ImportJob job) {
            return new ImportJobDto(
                    job.getId(),
                    job.getFileName(),
                    job.getStatus(),
                    job.getTotalRows(),
                    job.getSuccessRows(),
                    job.getErrorRows(),
                    job.getCommittedRows(),
                    job.getSummary(),
                    job.getQueuedAt(),
                    job.getStartedAt(),
                    job.getPreviewedAt(),
                    job.getCommittedAt(),
                    job.getCompletedAt(),
                    job.getFailedAt()
            );
        }
    }

    public record EmployeeImportPreviewRequest(
            String fileName,
            String csvContent,
            Map<String, String> fieldMapping
    ) {
    }

    public record ImportJobDetailDto(
            ImportJobDto job,
            List<String> detectedHeaders,
            Map<String, String> fieldMapping,
            List<ImportRowDto> rows
    ) {
    }

    public record ImportRowDto(
            UUID id,
            int rowNumber,
            String status,
            Map<String, String> rawValues,
            Map<String, String> mappedValues,
            List<ImportRowErrorDto> errors,
            UUID importedEmployeeId
    ) {
    }

    public record ImportRowErrorDto(
            int rowNumber,
            String field,
            String message
    ) {
    }

    public record TimesheetExportRowDto(
            UUID timesheetId,
            UUID employeeId,
            String employeeName,
            LocalDate weekStartDate,
            java.math.BigDecimal regularHours,
            java.math.BigDecimal overtimeHours,
            String status,
            Instant approvedAt,
            boolean lockedPayPeriod,
            String managerNote
    ) {
    }

    public record WebhookEventDto(
            UUID id,
            String eventType,
            String entityType,
            UUID entityId,
            String status,
            Instant generatedAt,
            WebhookDeliveryAttemptDto latestAttempt
    ) {
    }

    public record WebhookEventDetailDto(
            WebhookEventDto event,
            String payloadJson,
            List<WebhookDeliveryAttemptDto> attempts
    ) {
    }

    public record WebhookDeliveryAttemptDto(
            UUID id,
            String destinationName,
            String destinationUrl,
            String status,
            Integer responseCode,
            String responseBody,
            Instant attemptedAt
    ) {
        static WebhookDeliveryAttemptDto from(WebhookDeliveryAttempt attempt) {
            if (attempt == null) {
                return null;
            }
            return new WebhookDeliveryAttemptDto(
                    attempt.getId(),
                    attempt.getDestinationName(),
                    attempt.getDestinationUrl(),
                    attempt.getStatus(),
                    attempt.getResponseCode(),
                    attempt.getResponseBody(),
                    attempt.getAttemptedAt()
            );
        }
    }
}
