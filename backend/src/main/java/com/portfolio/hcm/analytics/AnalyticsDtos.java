package com.portfolio.hcm.analytics;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record AnalyticsEventRequest(
            @NotBlank String eventType,
            @NotBlank String visitorId,
            String path,
            String referrer,
            String metadataJson
    ) {
    }

    public record AnalyticsMetricDto(String label, long value) {
    }

    public record AnalyticsEventDto(
            String id,
            Instant occurredAt,
            String eventType,
            String path,
            String referrer,
            String accountEmail,
            String accountRole,
            String metadataJson
    ) {
        static AnalyticsEventDto from(AnalyticsEvent event) {
            return new AnalyticsEventDto(
                    event.getId().toString(),
                    event.getOccurredAt(),
                    event.getEventType(),
                    event.getPath(),
                    event.getReferrer(),
                    event.getAccountEmail(),
                    event.getAccountRole(),
                    event.getMetadataJson()
            );
        }
    }

    public record AnalyticsSummaryDto(
            long totalEvents,
            long totalVisits,
            long uniqueVisitors,
            long activeVisitors,
            Instant lastUsedAt,
            long totalLogins,
            List<AnalyticsMetricDto> topPages,
            List<AnalyticsMetricDto> loginRoles
    ) {
    }
}
