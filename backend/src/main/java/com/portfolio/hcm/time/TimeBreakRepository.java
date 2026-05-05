package com.portfolio.hcm.time;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeBreakRepository extends JpaRepository<TimeBreak, UUID> {
    List<TimeBreak> findByTimeEntryIdAndDeletedFalseOrderByBreakStartAtAsc(UUID timeEntryId);

    List<TimeBreak> findByTimeEntryIdInAndDeletedFalseOrderByBreakStartAtAsc(List<UUID> timeEntryIds);

    Optional<TimeBreak> findFirstByTenantIdAndTimeEntryIdAndBreakEndAtIsNullAndDeletedFalseOrderByBreakStartAtDesc(UUID tenantId, UUID timeEntryId);

    Optional<TimeBreak> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    long deleteByTenantId(UUID tenantId);
}
