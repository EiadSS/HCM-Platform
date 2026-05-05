package com.portfolio.hcm.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.portfolio.hcm.integration.ImportValidationService.EmployeeImportRecord;
import static com.portfolio.hcm.integration.ImportValidationService.ValidationContext;
import static org.assertj.core.api.Assertions.assertThat;

class ImportValidationServiceTest {
    private final ImportValidationService service = new ImportValidationService();

    @Test
    void reportsPerRowValidationErrorsForEnterpriseImportRules() {
        var errors = service.validate(List.of(
                row(2, "NS-004", "new.hire@example.com", "Retail", "Downtown Store", "manager@demo.hcm.local", "ACTIVE", "PART_TIME", "22.50", "24", "2026-05-01"),
                row(3, "NS-004", "bad.hire@example.com", "Unknown", "Downtown Store", "missing@demo.hcm.local", "BAD_STATUS", "NOT_REAL", "-1.00", "0", "bad-date"),
                row(4, "", "", "", "", "", "", "", "", "", "")
        ), context());

        assertThat(errors).extracting("field")
                .contains(
                        "employeeNumber",
                        "workEmail",
                        "department",
                        "managerEmail",
                        "status",
                        "employmentType",
                        "hourlyRate",
                        "weeklyHourCap",
                        "hireDate",
                        "location"
                );
    }

    private ValidationContext context() {
        return new ValidationContext(
                Set.of("ns-001"),
                Set.of("existing@demo.hcm.local"),
                Set.of("retail"),
                Set.of("downtown store"),
                Set.of("retail associate"),
                Set.of("manager@demo.hcm.local")
        );
    }

    private EmployeeImportRecord row(
            int rowNumber,
            String employeeNumber,
            String email,
            String department,
            String location,
            String managerEmail,
            String status,
            String employmentType,
            String hourlyRate,
            String weeklyCap,
            String hireDate
    ) {
        return new EmployeeImportRecord(
                rowNumber,
                employeeNumber,
                "New",
                "Hire",
                email,
                status,
                employmentType,
                department,
                location,
                "Retail Associate",
                managerEmail,
                hourlyRate,
                weeklyCap,
                hireDate
        );
    }
}
