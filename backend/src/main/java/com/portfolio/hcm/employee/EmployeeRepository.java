package com.portfolio.hcm.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByTenantIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(UUID tenantId);

    Optional<Employee> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<Employee> findByTenantIdAndUserAccountIdAndDeletedFalse(UUID tenantId, UUID userAccountId);

    boolean existsByTenantIdAndEmployeeNumberIgnoreCaseAndDeletedFalse(UUID tenantId, String employeeNumber);

    boolean existsByTenantIdAndWorkEmailIgnoreCaseAndDeletedFalse(UUID tenantId, String workEmail);

    long deleteByTenantId(UUID tenantId);
}
