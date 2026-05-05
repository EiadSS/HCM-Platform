package com.portfolio.hcm.dashboard;

import com.portfolio.hcm.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record DashboardResponse(
            String tenantName,
            Set<UserRole> roles,
            List<MetricCard> metrics,
            List<WorkItem> priorityWork,
            List<String> quickActions,
            Instant generatedAt
    ) {
    }

    public record MetricCard(String label, String value, String tone, String detail) {
    }

    public record WorkItem(String type, String title, String detail, String severity) {
    }

    static String money(BigDecimal value) {
        return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static String hours(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
