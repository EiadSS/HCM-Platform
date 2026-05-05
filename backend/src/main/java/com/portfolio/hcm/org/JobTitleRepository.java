package com.portfolio.hcm.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {
    List<JobTitle> findByTenantIdAndDeletedFalseOrderByName(UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
