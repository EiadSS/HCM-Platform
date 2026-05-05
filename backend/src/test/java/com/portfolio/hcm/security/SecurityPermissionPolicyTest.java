package com.portfolio.hcm.security;

import com.portfolio.hcm.audit.AuditController;
import com.portfolio.hcm.demo.DemoController;
import com.portfolio.hcm.integration.IntegrationController;
import com.portfolio.hcm.leave.LeaveController;
import com.portfolio.hcm.payroll.PayrollController;
import com.portfolio.hcm.schedule.ScheduleController;
import com.portfolio.hcm.time.TimesheetController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.portfolio.hcm.integration.IntegrationDtos.EmployeeImportPreviewRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveDecisionRequest;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewRequest;
import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityPermissionPolicyTest {
    @Test
    void sensitiveEndpointsKeepRecruiterVisibleRoleBoundaries() throws Exception {
        assertRoles(AuditController.class.getMethod("recent", Instant.class, Instant.class, String.class, String.class, String.class, UUID.class, Integer.class),
                "HR_ADMIN", "PAYROLL_ADMIN", "SYSTEM_ADMIN");
        assertRoles(IntegrationController.class.getMethod("previewEmployeeImport", EmployeeImportPreviewRequest.class),
                "HR_ADMIN", "SYSTEM_ADMIN");
        assertRoles(IntegrationController.class.getMethod("exportTimesheetsCsv"),
                "PAYROLL_ADMIN", "SYSTEM_ADMIN");
        assertRoles(PayrollController.class.getMethod("generate", PayrollPreviewRequest.class),
                "PAYROLL_ADMIN", "SYSTEM_ADMIN");
        assertRoles(ScheduleController.class.getMethod("createShift", LocalDate.class, ShiftRequest.class),
                "MANAGER", "HR_ADMIN", "SYSTEM_ADMIN");
        assertRoles(ScheduleController.class.getMethod("publish", LocalDate.class),
                "MANAGER", "HR_ADMIN", "SYSTEM_ADMIN");
        assertRoles(LeaveController.class.getMethod("approve", UUID.class, LeaveDecisionRequest.class),
                "MANAGER", "HR_ADMIN", "SYSTEM_ADMIN");
        assertRoles(LeaveController.class.getMethod("runAccruals", LeaveAccrualRunRequest.class),
                "HR_ADMIN", "SYSTEM_ADMIN");
        assertRoles(TimesheetController.class.getMethod("approve", UUID.class, com.portfolio.hcm.time.TimesheetDtos.ApprovalRequest.class),
                "MANAGER", "HR_ADMIN", "PAYROLL_ADMIN", "SYSTEM_ADMIN");
        assertRoles(TimesheetController.class.getMethod("lock", UUID.class),
                "PAYROLL_ADMIN", "SYSTEM_ADMIN");
        assertRoles(DemoController.class.getMethod("reset", String.class),
                "SYSTEM_ADMIN");
    }

    private void assertRoles(Method method, String... roles) {
        var annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(method.getName() + " must declare a PreAuthorize rule").isNotNull();
        assertThat(annotation.value()).contains(roles);
    }
}
