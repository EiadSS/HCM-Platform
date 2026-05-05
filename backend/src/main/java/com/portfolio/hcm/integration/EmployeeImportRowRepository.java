package com.portfolio.hcm.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeImportRowRepository extends JpaRepository<EmployeeImportRow, UUID> {
    List<EmployeeImportRow> findByTenantIdAndImportJobIdAndDeletedFalseOrderByRowNumberAsc(UUID tenantId, UUID importJobId);

    long deleteByTenantId(UUID tenantId);
}
