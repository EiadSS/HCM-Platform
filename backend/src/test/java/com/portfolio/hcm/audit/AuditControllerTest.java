package com.portfolio.hcm.audit;

import com.portfolio.hcm.common.GlobalExceptionHandler;
import com.portfolio.hcm.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest {
    private final AuditService service = mock(AuditService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(service, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPassesFilterQueryAndReturnsCompatibleDtos() throws Exception {
        var tenantId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        when(currentUserService.tenantId()).thenReturn(tenantId);
        when(service.search(eq(tenantId), any(AuditService.AuditLogQuery.class))).thenReturn(List.of(auditLog(tenantId, entityId)));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-05T00:00:00Z")
                        .param("actorEmail", "hr@demo.hcm.local")
                        .param("actionType", "employee.updated")
                        .param("entityType", "Employee")
                        .param("entityId", entityId.toString())
                        .param("limit", "75"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("employee.updated"))
                .andExpect(jsonPath("$[0].entityId").value(entityId.toString()))
                .andExpect(jsonPath("$[0].metadata").value(org.hamcrest.Matchers.containsString("employee-api")));

        var captor = org.mockito.ArgumentCaptor.forClass(AuditService.AuditLogQuery.class);
        verify(service).search(eq(tenantId), captor.capture());
        assertThat(captor.getValue().actorEmail()).isEqualTo("hr@demo.hcm.local");
        assertThat(captor.getValue().actionType()).isEqualTo("employee.updated");
        assertThat(captor.getValue().entityType()).isEqualTo("Employee");
        assertThat(captor.getValue().entityId()).isEqualTo(entityId);
        assertThat(captor.getValue().limit()).isEqualTo(75);
    }

    @Test
    void auditEndpointIsRestrictedToAdminRoles() throws Exception {
        var annotation = AuditController.class
                .getMethod("recent", Instant.class, Instant.class, String.class, String.class, String.class, UUID.class, Integer.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).contains("HR_ADMIN").contains("PAYROLL_ADMIN").contains("SYSTEM_ADMIN");
    }

    private AuditLog auditLog(UUID tenantId, UUID entityId) {
        var auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .actorEmail("hr@demo.hcm.local")
                .actionType("employee.updated")
                .entityType("Employee")
                .entityId(entityId)
                .previousValue("{\"status\":\"ACTIVE\"}")
                .newValue("{\"status\":\"ON_LEAVE\"}")
                .metadata("{\"source\":\"employee-api\"}")
                .build();
        auditLog.setCreatedAt(Instant.parse("2026-05-04T12:00:00Z"));
        auditLog.setUpdatedAt(Instant.parse("2026-05-04T12:00:00Z"));
        return auditLog;
    }
}
