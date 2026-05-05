package com.portfolio.hcm.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.hcm.common.BadRequestException;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.portfolio.hcm.integration.IntegrationDtos.WebhookDeliveryAttemptDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDetailDto;
import static com.portfolio.hcm.integration.IntegrationDtos.WebhookEventDto;

@Service
public class WebhookEventService {
    private static final String DESTINATION_NAME = "Northstar Demo Receiver";
    private static final String DESTINATION_URL = "https://integrations.demo.local/webhooks/northstar";

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public WebhookEventService(
            WebhookEventRepository webhookEventRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.attemptRepository = attemptRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEvent emit(UUID tenantId, String eventType, String entityType, UUID entityId, Map<String, ?> payload) {
        var event = webhookEventRepository.save(WebhookEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .payloadJson(json(payload))
                .status("DELIVERED")
                .generatedAt(Instant.now())
                .build());
        attemptRepository.save(successAttempt(tenantId, event.getId(), "Simulated 202 Accepted delivery"));
        return event;
    }

    @Transactional(readOnly = true)
    public List<WebhookEventDto> list() {
        var tenantId = currentUserService.tenantId();
        return webhookEventRepository.findByTenantIdAndDeletedFalseOrderByGeneratedAtDesc(tenantId).stream()
                .map(event -> dto(event, attemptRepository.findTopByTenantIdAndWebhookEventIdAndDeletedFalseOrderByAttemptedAtDesc(tenantId, event.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEventDetailDto detail(UUID id) {
        var tenantId = currentUserService.tenantId();
        var event = webhookEventRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found"));
        var attempts = attemptRepository.findByTenantIdAndWebhookEventIdAndDeletedFalseOrderByAttemptedAtDesc(tenantId, event.getId()).stream()
                .map(WebhookDeliveryAttemptDto::from)
                .toList();
        var latest = attempts.isEmpty() ? null : attempts.get(0);
        return new WebhookEventDetailDto(new WebhookEventDto(event.getId(), event.getEventType(), event.getEntityType(), event.getEntityId(), event.getStatus(), event.getGeneratedAt(), latest), event.getPayloadJson(), attempts);
    }

    @Transactional
    public WebhookEventDetailDto redeliver(UUID id) {
        var tenantId = currentUserService.tenantId();
        var event = webhookEventRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found"));
        attemptRepository.save(successAttempt(tenantId, event.getId(), "Manual retry accepted by demo receiver"));
        event.setStatus("DELIVERED");
        webhookEventRepository.save(event);
        return detail(id);
    }

    private WebhookEventDto dto(WebhookEvent event, WebhookDeliveryAttempt latestAttempt) {
        return new WebhookEventDto(
                event.getId(),
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getStatus(),
                event.getGeneratedAt(),
                WebhookDeliveryAttemptDto.from(latestAttempt)
        );
    }

    private WebhookDeliveryAttempt successAttempt(UUID tenantId, UUID eventId, String responseBody) {
        return WebhookDeliveryAttempt.builder()
                .tenantId(tenantId)
                .webhookEventId(eventId)
                .destinationName(DESTINATION_NAME)
                .destinationUrl(DESTINATION_URL)
                .status("DELIVERED")
                .responseCode(202)
                .responseBody(responseBody)
                .attemptedAt(Instant.now())
                .build();
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to serialize webhook payload");
        }
    }
}
