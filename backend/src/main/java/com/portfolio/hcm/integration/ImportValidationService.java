package com.portfolio.hcm.integration;

import com.portfolio.hcm.employee.EmployeeStatus;
import com.portfolio.hcm.employee.EmploymentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ImportValidationService {
    private static final List<String> REQUIRED_FIELDS = List.of(
            "employeeNumber",
            "firstName",
            "lastName",
            "workEmail",
            "status",
            "employmentType",
            "department",
            "location",
            "hourlyRate",
            "weeklyHourCap",
            "hireDate"
    );

    public List<RowValidationError> validate(List<EmployeeImportRecord> rows, ValidationContext context) {
        var errors = new java.util.ArrayList<RowValidationError>();
        var seenEmployeeNumbers = new HashSet<String>();
        var seenEmails = new HashSet<String>();
        for (var row : rows) {
            for (var field : REQUIRED_FIELDS) {
                if (isBlank(row.value(field))) {
                    errors.add(new RowValidationError(row.rowNumber(), field, humanize(field) + " is required"));
                }
            }

            var normalizedNumber = normalize(row.employeeNumber());
            if (!normalizedNumber.isBlank()) {
                if (!seenEmployeeNumbers.add(normalizedNumber)) {
                    errors.add(new RowValidationError(row.rowNumber(), "employeeNumber", "Duplicate employee ID in file"));
                }
                if (context.existingEmployeeNumbers().contains(normalizedNumber)) {
                    errors.add(new RowValidationError(row.rowNumber(), "employeeNumber", "Employee ID conflicts with an existing employee"));
                }
            }

            var normalizedEmail = normalize(row.workEmail());
            if (!normalizedEmail.isBlank()) {
                if (!seenEmails.add(normalizedEmail)) {
                    errors.add(new RowValidationError(row.rowNumber(), "workEmail", "Duplicate work email in file"));
                }
                if (context.existingWorkEmails().contains(normalizedEmail)) {
                    errors.add(new RowValidationError(row.rowNumber(), "workEmail", "Work email conflicts with an existing employee"));
                }
            }

            if (!isBlank(row.managerEmail()) && !context.validManagerEmails().contains(normalize(row.managerEmail()))) {
                errors.add(new RowValidationError(row.rowNumber(), "managerEmail", "Invalid manager email"));
            }
            if (!isBlank(row.department()) && !context.validDepartments().contains(normalize(row.department()))) {
                errors.add(new RowValidationError(row.rowNumber(), "department", "Invalid department"));
            }
            if (!isBlank(row.location()) && !context.validLocations().contains(normalize(row.location()))) {
                errors.add(new RowValidationError(row.rowNumber(), "location", "Invalid location"));
            }
            if (!isBlank(row.jobTitle()) && !context.validJobTitles().contains(normalize(row.jobTitle()))) {
                errors.add(new RowValidationError(row.rowNumber(), "jobTitle", "Invalid job title"));
            }
            if (!isBlank(row.status()) && !isEnumValue(EmployeeStatus.class, row.status())) {
                errors.add(new RowValidationError(row.rowNumber(), "status", "Invalid employment status"));
            }
            if (!isBlank(row.employmentType()) && !isEnumValue(EmploymentType.class, row.employmentType())) {
                errors.add(new RowValidationError(row.rowNumber(), "employmentType", "Invalid employment type"));
            }
            if (!isPositiveDecimal(row.hourlyRate())) {
                errors.add(new RowValidationError(row.rowNumber(), "hourlyRate", "Invalid pay rate"));
            }
            if (!isPositiveDecimal(row.weeklyHourCap())) {
                errors.add(new RowValidationError(row.rowNumber(), "weeklyHourCap", "Invalid weekly hour cap"));
            }
            if (!isBlank(row.hireDate()) && !isDate(row.hireDate())) {
                errors.add(new RowValidationError(row.rowNumber(), "hireDate", "Invalid hire date"));
            }
        }
        return errors;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String humanize(String value) {
        return value.replaceAll("([A-Z])", " $1").replaceFirst("^.", value.substring(0, 1).toUpperCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPositiveDecimal(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            return new BigDecimal(value.trim()).compareTo(BigDecimal.ZERO) > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static <T extends Enum<T>> boolean isEnumValue(Class<T> enumClass, String value) {
        try {
            Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public record EmployeeImportRecord(
            int rowNumber,
            String employeeNumber,
            String firstName,
            String lastName,
            String workEmail,
            String status,
            String employmentType,
            String department,
            String location,
            String jobTitle,
            String managerEmail,
            String hourlyRate,
            String weeklyHourCap,
            String hireDate
    ) {
        String value(String field) {
            return switch (field) {
                case "employeeNumber" -> employeeNumber;
                case "firstName" -> firstName;
                case "lastName" -> lastName;
                case "workEmail" -> workEmail;
                case "status" -> status;
                case "employmentType" -> employmentType;
                case "department" -> department;
                case "location" -> location;
                case "hourlyRate" -> hourlyRate;
                case "weeklyHourCap" -> weeklyHourCap;
                case "hireDate" -> hireDate;
                default -> null;
            };
        }
    }

    public record ValidationContext(
            Set<String> existingEmployeeNumbers,
            Set<String> existingWorkEmails,
            Set<String> validDepartments,
            Set<String> validLocations,
            Set<String> validJobTitles,
            Set<String> validManagerEmails
    ) {
    }

    public record RowValidationError(int rowNumber, String field, String message) {
    }
}
