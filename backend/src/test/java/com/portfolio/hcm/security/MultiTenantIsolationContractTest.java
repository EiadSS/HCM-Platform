package com.portfolio.hcm.security;

import com.portfolio.hcm.audit.AuditLogRepository;
import com.portfolio.hcm.employee.EmployeeRepository;
import com.portfolio.hcm.integration.ImportJobRepository;
import com.portfolio.hcm.integration.WebhookEventRepository;
import com.portfolio.hcm.payroll.PayrollPreviewRepository;
import com.portfolio.hcm.time.TimesheetRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MultiTenantIsolationContractTest {
    @Test
    void representativeReadRepositoriesExposeTenantScopedQueriesOnly() {
        assertRepositoryHasTenantRead(EmployeeRepository.class, "findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc");
        assertRepositoryHasTenantRead(AuditLogRepository.class, "search");
        assertRepositoryHasTenantRead(PayrollPreviewRepository.class, "findByTenantIdAndDeletedFalseOrderByPeriodStartDesc");
        assertRepositoryHasTenantRead(ImportJobRepository.class, "findByTenantIdAndDeletedFalseOrderByCreatedAtDesc");
        assertRepositoryHasTenantRead(WebhookEventRepository.class, "findByTenantIdAndDeletedFalseOrderByGeneratedAtDesc");
        assertRepositoryHasTenantRead(TimesheetRepository.class, "findByTenantIdAndDeletedFalseOrderByWeekStartDateDesc");
    }

    @Test
    void representativeDetailRepositoriesRequireTenantIdWithEntityId() {
        assertRepositoryHasTenantRead(EmployeeRepository.class, "findByIdAndTenantIdAndDeletedFalse");
        assertRepositoryHasTenantRead(PayrollPreviewRepository.class, "findByIdAndTenantIdAndDeletedFalse");
        assertRepositoryHasTenantRead(ImportJobRepository.class, "findByIdAndTenantIdAndDeletedFalse");
        assertRepositoryHasTenantRead(WebhookEventRepository.class, "findByIdAndTenantIdAndDeletedFalse");
        assertRepositoryHasTenantRead(TimesheetRepository.class, "findByIdAndTenantIdAndDeletedFalse");
    }

    private void assertRepositoryHasTenantRead(Class<?> repository, String methodName) {
        assertThat(Arrays.stream(repository.getMethods()).anyMatch(method -> method.getName().equals(methodName)))
                .as(repository.getSimpleName() + " should expose " + methodName)
                .isTrue();
    }
}
