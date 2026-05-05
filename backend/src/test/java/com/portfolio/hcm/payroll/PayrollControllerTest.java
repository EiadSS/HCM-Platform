package com.portfolio.hcm.payroll;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDetailDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewLineDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayrollControllerTest {
    private final PayrollPreviewService service = mock(PayrollPreviewService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PayrollController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void generateEndpointReturnsPreviewDetailAndPassesRequest() throws Exception {
        when(service.generate(any(PayrollPreviewRequest.class))).thenReturn(detail());
        var request = new PayrollPreviewRequest(LocalDate.of(2026, 4, 27), LocalDate.of(2026, 5, 3), null);

        mockMvc.perform(post("/api/v1/payroll/previews/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview.grossPay").value(1174.13))
                .andExpect(jsonPath("$.lines[0].employeeName").value("Amara Singh"));

        verify(service).generate(any(PayrollPreviewRequest.class));
    }

    @Test
    void detailEndpointReturnsPersistedLines() throws Exception {
        var id = UUID.randomUUID();
        when(service.detail(id)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/payroll/previews/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].explanation").value(org.hamcrest.Matchers.containsString("holiday")));
    }

    @Test
    void generateEndpointIsPayrollAndSystemOnly() throws Exception {
        var annotation = PayrollController.class
                .getMethod("generate", PayrollPreviewRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).contains("PAYROLL_ADMIN").contains("SYSTEM_ADMIN");
    }

    private PayrollPreviewDetailDto detail() {
        var previewId = UUID.randomUUID();
        var preview = new PayrollPreviewDto(
                previewId,
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 5, 3),
                null,
                new BigDecimal("40.00"),
                new BigDecimal("4.00"),
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("9.00"),
                new BigDecimal("104.63"),
                new BigDecimal("1174.13"),
                1,
                1,
                "GENERATED",
                "Generated gross-pay preview"
        );
        var line = new PayrollPreviewLineDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Amara Singh",
                UUID.randomUUID(),
                "Downtown Store",
                1,
                new BigDecimal("23.25"),
                new BigDecimal("40.00"),
                new BigDecimal("4.00"),
                new BigDecimal("9.00"),
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("930.00"),
                new BigDecimal("139.50"),
                new BigDecimal("104.63"),
                new BigDecimal("1174.13"),
                "Downtown Store rule",
                "Amara holiday premium explanation"
        );
        return new PayrollPreviewDetailDto(preview, List.of(line));
    }
}
