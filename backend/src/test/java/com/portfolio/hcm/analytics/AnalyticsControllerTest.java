package com.portfolio.hcm.analytics;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsEventDto;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsMetricDto;
import static com.portfolio.hcm.analytics.AnalyticsDtos.AnalyticsSummaryDto;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsControllerTest {
    private final AnalyticsService service = mock(AnalyticsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicEventIngestionWorksWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/site-stats/record")
                        .contentType("application/json")
                        .content("""
                                {"eventType":"PAGE_VIEW","visitorId":"visitor-1","path":"/login","referrer":"https://github.com"}
                                """))
                .andExpect(status().isNoContent());

        verify(service).recordPublicEvent(any());
    }

    @Test
    void ownerSummaryRejectsInvalidKey() throws Exception {
        doThrow(new ForbiddenOperationException("Invalid analytics owner code"))
                .when(service).summary(eq("wrong"), any(), any());

        mockMvc.perform(get("/api/v1/owner/site-stats/summary")
                        .header("X-Owner-Analytics-Key", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid analytics owner code"));
    }

    @Test
    void ownerSummaryAcceptsValidKey() throws Exception {
        when(service.summary(eq("owner-code"), any(), any())).thenReturn(new AnalyticsSummaryDto(
                8,
                5,
                2,
                1,
                Instant.parse("2026-05-12T15:00:00Z"),
                3,
                List.of(new AnalyticsMetricDto("/", 4)),
                List.of(new AnalyticsMetricDto("SYSTEM_ADMIN", 2))
        ));

        mockMvc.perform(get("/api/v1/owner/site-stats/summary")
                        .header("X-Owner-Analytics-Key", "owner-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVisits").value(5))
                .andExpect(jsonPath("$.uniqueVisitors").value(2))
                .andExpect(jsonPath("$.topPages[0].label").value("/"));
    }

    @Test
    void ownerEventsReturnsRecentActivity() throws Exception {
        when(service.recentEvents("owner-code", 25)).thenReturn(List.of(new AnalyticsEventDto(
                "event-1",
                Instant.parse("2026-05-12T15:00:00Z"),
                "LOGIN_SUCCESS",
                "/login",
                null,
                "admin@demo.hcm.local",
                "SYSTEM_ADMIN",
                "{\"source\":\"auth\"}"
        )));

        mockMvc.perform(get("/api/v1/owner/site-stats/recent")
                        .header("X-Owner-Analytics-Key", "owner-code")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$[0].accountRole").value("SYSTEM_ADMIN"));
    }
}
