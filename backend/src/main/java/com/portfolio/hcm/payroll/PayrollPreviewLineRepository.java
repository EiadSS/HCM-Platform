package com.portfolio.hcm.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayrollPreviewLineRepository extends JpaRepository<PayrollPreviewLine, UUID> {
    List<PayrollPreviewLine> findByTenantIdAndPayrollPreviewIdAndDeletedFalseOrderByEmployeeNameAsc(UUID tenantId, UUID payrollPreviewId);

    long deleteByTenantId(UUID tenantId);
}
