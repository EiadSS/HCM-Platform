package com.portfolio.hcm.analytics;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventDto;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventRequest;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsSummaryDto;

@RestController
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/api/v1/analytics/events")
    public ResponseEntity<Void> recordEvent(@Valid @RequestBody AnalyticsEventRequest request) {
        analyticsService.recordPublicEvent(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/owner/analytics/summary")
    public AnalyticsSummaryDto summary(
            @RequestHeader(value = "X-Owner-Analytics-Key", required = false) String ownerKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return analyticsService.summary(ownerKey, from, to);
    }

    @GetMapping("/api/v1/owner/analytics/events")
    public List<AnalyticsEventDto> events(
            @RequestHeader(value = "X-Owner-Analytics-Key", required = false) String ownerKey,
            @RequestParam(required = false) Integer limit
    ) {
        return analyticsService.recentEvents(ownerKey, limit);
    }
}
