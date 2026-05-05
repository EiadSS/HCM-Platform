package com.portfolio.hcm.audit;

import com.portfolio.hcm.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {
    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AuditService service = new AuditService(repository, currentUserService);

    @Test
    void searchAppliesFiltersAndClampsLimitWithinTenant() {
        var tenantId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        var from = Instant.parse("2026-05-01T00:00:00Z");
        var to = Instant.parse("2026-05-05T00:00:00Z");
        when(repository.search(
                eq(tenantId),
                eq(from),
                eq(to),
                eq("hr@demo.hcm.local"),
                eq("employee.updated"),
                eq("Employee"),
                eq(entityId),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(auditLog(tenantId, "employee.updated")));

        var result = service.search(tenantId, new AuditService.AuditLogQuery(
                from,
                to,
                " hr@demo.hcm.local ",
                " employee.updated ",
                " Employee ",
                entityId,
                250
        ));

        assertThat(result).hasSize(1);
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(tenantId), eq(from), eq(to), eq("hr@demo.hcm.local"), eq("employee.updated"), eq("Employee"), eq(entityId), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void recentKeepsLegacyTwentyFiveRowBehavior() {
        var tenantId = UUID.randomUUID();
        service.recent(tenantId);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(tenantId), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }

    private AuditLog auditLog(UUID tenantId, String actionType) {
        var auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .actorEmail("hr@demo.hcm.local")
                .actionType(actionType)
                .entityType("Employee")
                .build();
        auditLog.setCreatedAt(Instant.parse("2026-05-04T12:00:00Z"));
        auditLog.setUpdatedAt(Instant.parse("2026-05-04T12:00:00Z"));
        return auditLog;
    }
}
