package com.portfolio.hcm.employee;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class EmployeeDtos {
    private EmployeeDtos() {
    }

    public record EmployeeResponse(
            UUID id,
            String employeeNumber,
            String firstName,
            String lastName,
            String fullName,
            String workEmail,
            EmployeeStatus status,
            EmploymentType employmentType,
            UUID departmentId,
            String departmentName,
            UUID locationId,
            String locationName,
            UUID jobTitleId,
            String jobTitleName,
            UUID managerEmployeeId,
            String managerName,
            BigDecimal hourlyRate,
            BigDecimal weeklyHourCap,
            LocalDate hireDate,
            LocalDate terminationDate
    ) {
    }

    public record EmployeeRequest(
            @NotBlank String employeeNumber,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String workEmail,
            @NotNull EmployeeStatus status,
            @NotNull EmploymentType employmentType,
            UUID departmentId,
            UUID locationId,
            UUID jobTitleId,
            UUID managerEmployeeId,
            @NotNull @DecimalMin("0.00") BigDecimal hourlyRate,
            @NotNull @DecimalMin("0.00") BigDecimal weeklyHourCap,
            @NotNull LocalDate hireDate
    ) {
    }
}
