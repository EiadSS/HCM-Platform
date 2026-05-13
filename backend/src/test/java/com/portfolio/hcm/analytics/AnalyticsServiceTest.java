package com.portfolio.hcm.analytics;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.user.AccountStatus;
import com.portfolio.hcm.user.UserAccount;
import com.portfolio.hcm.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    private final AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
    private final AnalyticsService service = new AnalyticsService(repository, "owner-code", true);

    @Test
    void recordsPublicPageViewWithHashedVisitor() {
        service.recordPublicEvent(new AnalyticsEventRequest("page_view", "visitor-123", "/", "https://github.com", "{\"source\":\"test\"}"));

        var captor = org.mockito.ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("PAGE_VIEW");
        assertThat(saved.getPath()).isEqualTo("/");
        assertThat(saved.getVisitorHash()).isNotBlank().isNotEqualTo("visitor-123");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void recordsSuccessfulLoginWithRoleAndTenantContext() {
        var tenantId = UUID.randomUUID();
        var user = UserAccount.builder()
                .tenantId(tenantId)
                .email("admin@demo.hcm.local")
                .displayName("System Admin")
                .passwordHash("hash")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(UserRole.SYSTEM_ADMIN))
                .build();

        service.recordLogin(user, "visitor-123");

        var captor = org.mockito.ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getAccountEmail()).isEqualTo("admin@demo.hcm.local");
        assertThat(saved.getAccountRole()).isEqualTo("SYSTEM_ADMIN");
    }

    @Test
    void summaryRejectsMissingOrInvalidOwnerKey() {
        assertThatThrownBy(() -> service.summary(null, null, null)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.summary("wrong", null, null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void summaryAcceptsOwnerKeyAndBuildsMetrics() {
        when(repository.countEvents(any(), any())).thenReturn(8L);
        when(repository.countByEventType(eq("PAGE_VIEW"), any(), any())).thenReturn(5L);
        when(repository.countByEventType(eq("LOGIN_SUCCESS"), any(), any())).thenReturn(3L);
        when(repository.countUniqueVisitors(any(), any())).thenReturn(2L);
        when(repository.countActiveVisitors(any())).thenReturn(1L);
        when(repository.findLastUsedAt(any(), any())).thenReturn(java.util.Optional.of(Instant.parse("2026-05-12T15:00:00Z")));
        when(repository.topPages(any(), any(), any(Pageable.class))).thenReturn(List.<Object[]>of(new Object[]{"/", 4L}));
        when(repository.loginRoles(any(), any(), any(Pageable.class))).thenReturn(List.<Object[]>of(new Object[]{"SYSTEM_ADMIN", 2L}));

        var summary = service.summary("owner-code", null, null);

        assertThat(summary.totalEvents()).isEqualTo(8);
        assertThat(summary.totalVisits()).isEqualTo(5);
        assertThat(summary.uniqueVisitors()).isEqualTo(2);
        assertThat(summary.activeVisitors()).isEqualTo(1);
        assertThat(summary.lastUsedAt()).isEqualTo(Instant.parse("2026-05-12T15:00:00Z"));
        assertThat(summary.totalLogins()).isEqualTo(3);
        assertThat(summary.topPages()).extracting("label").contains("/");
        assertThat(summary.loginRoles()).extracting("label").contains("SYSTEM_ADMIN");
    }
}
