package com.portfolio.hcm.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCaseAndDeletedFalse(String email);

    long deleteByTenantId(UUID tenantId);
}
