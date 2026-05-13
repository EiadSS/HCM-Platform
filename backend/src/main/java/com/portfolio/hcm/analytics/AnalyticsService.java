package com.portfolio.hcm.analytics;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.user.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventDto;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventRequest;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsMetricDto;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsSummaryDto;

@Service
public class AnalyticsService {
    static final String PAGE_VIEW = "PAGE_VIEW";
    static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final AnalyticsEventRepository analyticsEventRepository;
    private final String ownerKey;
    private final boolean enabled;

    public AnalyticsService(
            AnalyticsEventRepository analyticsEventRepository,
            @Value("${app.analytics.owner-key}") String ownerKey,
            @Value("${app.analytics.enabled:true}") boolean enabled
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.ownerKey = ownerKey;
        this.enabled = enabled;
    }

    @Transactional
    public void recordPublicEvent(AnalyticsEventRequest request) {
        if (!enabled) {
            return;
        }
        analyticsEventRepository.save(AnalyticsEvent.builder()
                .eventType(cleanEventType(request.eventType()))
                .visitorHash(hashVisitor(request.visitorId()))
                .path(clean(request.path(), 500))
                .referrer(clean(request.referrer(), 1000))
                .metadataJson(clean(request.metadataJson(), 4000))
                .occurredAt(Instant.now())
                .build());
    }

    @Transactional
    public void recordLogin(UserAccount user, String visitorId) {
        if (!enabled) {
            return;
        }
        analyticsEventRepository.save(AnalyticsEvent.builder()
                .eventType(LOGIN_SUCCESS)
                .visitorHash(hashVisitor(visitorId))
                .tenantId(user.getTenantId())
                .accountEmail(user.getEmail())
                .accountRole(user.getRoles().stream().findFirst().map(Enum::name).orElse("UNKNOWN"))
                .path("/login")
                .metadataJson("{\"source\":\"auth\"}")
                .occurredAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto summary(String suppliedKey, Instant from, Instant to) {
        validateOwnerKey(suppliedKey);
        var range = normalizedRange(from, to);
        var topPages = analyticsEventRepository.topPages(range.from(), range.to(), PageRequest.of(0, 5)).stream()
                .map(row -> new AnalyticsMetricDto((String) row[0], (Long) row[1]))
                .toList();
        var loginRoles = analyticsEventRepository.loginRoles(range.from(), range.to(), PageRequest.of(0, 8)).stream()
                .map(row -> new AnalyticsMetricDto((String) row[0], (Long) row[1]))
                .toList();
        return new AnalyticsSummaryDto(
                analyticsEventRepository.countEvents(range.from(), range.to()),
                analyticsEventRepository.countByEventType(PAGE_VIEW, range.from(), range.to()),
                analyticsEventRepository.countUniqueVisitors(range.from(), range.to()),
                analyticsEventRepository.countActiveVisitors(Instant.now().minus(10, ChronoUnit.MINUTES)),
                analyticsEventRepository.findLastUsedAt(range.from(), range.to()).orElse(null),
                analyticsEventRepository.countByEventType(LOGIN_SUCCESS, range.from(), range.to()),
                topPages,
                loginRoles
        );
    }

    @Transactional(readOnly = true)
    public List<AnalyticsEventDto> recentEvents(String suppliedKey, Integer limit) {
        validateOwnerKey(suppliedKey);
        var clampedLimit = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        return analyticsEventRepository.findRecent(Instant.EPOCH, Instant.now().plus(1, ChronoUnit.DAYS), PageRequest.of(0, clampedLimit))
                .stream()
                .map(AnalyticsEventDto::from)
                .toList();
    }

    private void validateOwnerKey(String suppliedKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new ForbiddenOperationException("Analytics owner code is not configured");
        }
        if (suppliedKey == null || suppliedKey.isBlank()) {
            throw new ForbiddenOperationException("Analytics owner code is required");
        }
        var expected = ownerKey.getBytes(StandardCharsets.UTF_8);
        var supplied = suppliedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ForbiddenOperationException("Invalid analytics owner code");
        }
    }

    private AnalyticsRange normalizedRange(Instant from, Instant to) {
        var end = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to;
        var start = from == null ? Instant.EPOCH : from;
        if (start.isAfter(end)) {
            return new AnalyticsRange(end, start);
        }
        return new AnalyticsRange(start, end);
    }

    private String hashVisitor(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(visitorId.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for analytics visitor hashing", ex);
        }
    }

    private String cleanEventType(String eventType) {
        var cleaned = clean(eventType, 80);
        if (cleaned == null) {
            return PAGE_VIEW;
        }
        return cleaned.replace('-', '_').replace(' ', '_').toUpperCase();
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private record AnalyticsRange(Instant from, Instant to) {
    }
}
