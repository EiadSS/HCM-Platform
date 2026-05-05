package com.portfolio.hcm.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.hcm.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimesheetControllerTest {
    private final TimesheetService service = mock(TimesheetService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TimesheetController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void submitEndpointReturnsUpdatedTimesheet() throws Exception {
        var id = UUID.randomUUID();
        when(service.submit(id)).thenReturn(timesheet(id, TimesheetStatus.SUBMITTED, false));

        mockMvc.perform(post("/api/v1/timesheets/{id}/submit", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void lockEndpointReturnsLockedTimesheet() throws Exception {
        var id = UUID.randomUUID();
        when(service.lock(id)).thenReturn(timesheet(id, TimesheetStatus.APPROVED, true));

        mockMvc.perform(post("/api/v1/timesheets/{id}/lock", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedPayPeriod").value(true));
    }

    @Test
    void manualEntryEndpointPassesRequestToService() throws Exception {
        var id = UUID.randomUUID();
        var request = new TimesheetDtos.ManualTimeEntryRequest(
                Instant.parse("2026-05-04T13:00:00Z"),
                Instant.parse("2026-05-04T21:00:00Z"),
                null,
                null,
                "Manual correction"
        );

        mockMvc.perform(post("/api/v1/timesheets/{id}/entries", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(service).addEntry(eq(id), any(TimesheetDtos.ManualTimeEntryRequest.class));
    }

    private TimesheetDtos.TimesheetDto timesheet(UUID id, TimesheetStatus status, boolean locked) {
        return new TimesheetDtos.TimesheetDto(
                id,
                UUID.randomUUID(),
                "Jordan Kim",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("40.00"),
                BigDecimal.ZERO.setScale(2),
                status,
                locked,
                null,
                null,
                null
        );
    }
}
