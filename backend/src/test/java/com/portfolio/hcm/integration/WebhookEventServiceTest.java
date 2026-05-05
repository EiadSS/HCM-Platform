package com.portfolio.hcm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.hcm.security.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookEventServiceTest {
    @Test
    void emitsWebhookEventAndSimulatedDeliveryAttempt() {
        var tenantId = UUID.randomUUID();
        var eventRepository = mock(WebhookEventRepository.class);
        var attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        var currentUserService = mock(CurrentUserService.class);
        var service = new WebhookEventService(eventRepository, attemptRepository, currentUserService, new ObjectMapper());
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var event = service.emit(tenantId, "employee.updated", "Employee", UUID.randomUUID(), Map.of("employeeNumber", "NS-004"));

        assertThat(event.getStatus()).isEqualTo("DELIVERED");
        assertThat(event.getPayloadJson()).contains("NS-004");
    }

    @Test
    void redeliveryAddsAttemptAndMarksEventDelivered() {
        var tenantId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var event = WebhookEvent.builder()
                .tenantId(tenantId)
                .eventType("payroll.preview.generated")
                .entityType("PayrollPreview")
                .entityId(UUID.randomUUID())
                .payloadJson("{\"grossPay\":1740.42}")
                .status("FAILED")
                .generatedAt(java.time.Instant.now())
                .build();
        event.setId(eventId);
        var eventRepository = mock(WebhookEventRepository.class);
        var attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        var currentUserService = mock(CurrentUserService.class);
        var service = new WebhookEventService(eventRepository, attemptRepository, currentUserService, new ObjectMapper());
        when(currentUserService.tenantId()).thenReturn(tenantId);
        when(eventRepository.findByIdAndTenantIdAndDeletedFalse(eventId, tenantId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.findByTenantIdAndWebhookEventIdAndDeletedFalseOrderByAttemptedAtDesc(tenantId, eventId)).thenReturn(java.util.List.of());

        service.redeliver(eventId);

        assertThat(event.getStatus()).isEqualTo("DELIVERED");
    }
}
