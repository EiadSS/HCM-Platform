package com.portfolio.hcm.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByTenantIdAndDeletedFalseOrderByName(UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
