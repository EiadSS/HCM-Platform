package com.portfolio.hcm.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    List<Location> findByTenantIdAndDeletedFalseOrderByName(UUID tenantId);

    Optional<Location> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
