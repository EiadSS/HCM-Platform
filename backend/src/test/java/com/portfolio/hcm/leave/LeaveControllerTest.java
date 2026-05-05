package com.portfolio.hcm.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.hcm.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunResult;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveBalanceDto;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveDecisionRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestCreate;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaveControllerTest {
    private final LeaveService service = mock(LeaveService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LeaveController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createEndpointReturnsRequestWithConflictWarning() throws Exception {
        when(service.create(any(LeaveRequestCreate.class))).thenReturn(requestDto("PENDING", true));
        var request = new LeaveRequestCreate("VACATION", LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 13), new BigDecimal("16.00"), "Family trip");

        mockMvc.perform(post("/api/v1/leave/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveType").value("VACATION"))
                .andExpect(jsonPath("$.conflict").value(true))
                .andExpect(jsonPath("$.conflictSummary").value(org.hamcrest.Matchers.containsString("scheduled shift")));

        verify(service).create(any(LeaveRequestCreate.class));
    }

    @Test
    void approveEndpointReturnsUpdatedBalanceWorkflowState() throws Exception {
        var id = UUID.randomUUID();
        when(service.approve(any(UUID.class), any(LeaveDecisionRequest.class))).thenReturn(requestDto("APPROVED", false));

        mockMvc.perform(post("/api/v1/leave/requests/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveDecisionRequest("Approved with coverage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionNote").value("Approved with coverage"));

        verify(service).approve(any(UUID.class), any(LeaveDecisionRequest.class));
    }

    @Test
    void balanceEndpointReturnsAvailableHours() throws Exception {
        var employeeId = UUID.randomUUID();
        when(service.balances(employeeId)).thenReturn(List.of(new LeaveBalanceDto(
                UUID.randomUUID(),
                employeeId,
                "Jordan Kim",
                "VACATION",
                new BigDecimal("58.00"),
                new BigDecimal("8.00"),
                new BigDecimal("16.00"),
                new BigDecimal("34.00"),
                new BigDecimal("80.00")
        )));

        mockMvc.perform(get("/api/v1/leave/balances").param("employeeId", employeeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("VACATION"))
                .andExpect(jsonPath("$[0].availableHours").value(34.0));
    }

    @Test
    void accrualEndpointReturnsRunSummary() throws Exception {
        when(service.runAccruals(any(LeaveAccrualRunRequest.class))).thenReturn(new LeaveAccrualRunResult(LocalDate.of(2026, 5, 1), 6, new BigDecimal("36.00")));

        mockMvc.perform(post("/api/v1/leave/accruals/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveAccrualRunRequest(LocalDate.of(2026, 5, 20)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accrualPeriod[0]").value(2026))
                .andExpect(jsonPath("$.accrualPeriod[1]").value(5))
                .andExpect(jsonPath("$.accrualPeriod[2]").value(1))
                .andExpect(jsonPath("$.balancesUpdated").value(6));
    }

    @Test
    void decisionAndAccrualEndpointsAreRoleSecured() throws Exception {
        var approve = LeaveController.class
                .getMethod("approve", UUID.class, LeaveDecisionRequest.class)
                .getAnnotation(PreAuthorize.class);
        var reject = LeaveController.class
                .getMethod("reject", UUID.class, LeaveDecisionRequest.class)
                .getAnnotation(PreAuthorize.class);
        var accrual = LeaveController.class
                .getMethod("runAccruals", LeaveAccrualRunRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(approve.value()).contains("MANAGER").contains("HR_ADMIN").contains("SYSTEM_ADMIN");
        assertThat(reject.value()).contains("MANAGER").contains("HR_ADMIN").contains("SYSTEM_ADMIN");
        assertThat(accrual.value()).contains("HR_ADMIN").contains("SYSTEM_ADMIN");
    }

    private LeaveRequestDto requestDto(String status, boolean conflict) {
        return new LeaveRequestDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Jordan Kim",
                UUID.randomUUID(),
                "VACATION",
                LocalDate.of(2026, 5, 12),
                LocalDate.of(2026, 5, 13),
                new BigDecimal("16.00"),
                status,
                conflict,
                conflict ? 2 : 0,
                conflict ? "Conflicts with 2 scheduled shift(s): 2026-05-12, 2026-05-13" : null,
                Instant.parse("2026-05-04T12:00:00Z"),
                status.equals("APPROVED") ? UUID.randomUUID() : null,
                status.equals("APPROVED") ? Instant.parse("2026-05-04T13:00:00Z") : null,
                "Family trip",
                "Approved with coverage",
                status.equals("APPROVED") ? "Approved with coverage" : null
        );
    }
}
