package varga.supportplane.service;

import varga.supportplane.model.Notification;
import varga.supportplane.model.Tenant;
import varga.supportplane.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Dispatch notifications for a given event type on a tenant.
     * Finds all enabled notification configs matching the event type and sends them.
     */
    public void dispatch(Tenant tenant, String eventType, String subject, String body) {
        if (tenant == null) return;

        List<Notification> configs = notificationRepository.findByTenantId(tenant.getId()).stream()
                .filter(n -> n.getEnabled() && eventType.equals(n.getType()))
                .toList();

        for (Notification config : configs) {
            try {
                switch (config.getChannel()) {
                    case "WEBHOOK" -> sendWebhook(config, eventType, subject, body);
                    case "SLACK" -> sendSlack(config, subject, body);
                    case "EMAIL" -> logEmail(config, subject, body); // Real email requires SMTP config
                    default -> log.warn("Unknown notification channel: {}", config.getChannel());
                }
            } catch (Exception e) {
                log.warn("Failed to send {} notification for event {}: {}", config.getChannel(), eventType, e.getMessage());
            }
        }
    }

    private void sendWebhook(Notification config, String eventType, String subject, String body) {
        Map<String, Object> configMap = config.getConfig();
        String url = configMap != null ? (String) configMap.getOrDefault("url", configMap.get("webhookUrl")) : null;
        if (url == null || url.isBlank()) return;

        Map<String, Object> payload = Map.of(
                "event", eventType,
                "subject", subject,
                "body", body,
                "tenant", config.getTenant().getTenantId(),
                "timestamp", java.time.Instant.now().toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Void.class);
        log.info("Webhook sent to {} for event {}", url, eventType);
    }

    private void sendSlack(Notification config, String subject, String body) {
        Map<String, Object> configMap = config.getConfig();
        String webhookUrl = configMap != null ? (String) configMap.getOrDefault("url", configMap.get("webhookUrl")) : null;
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        Map<String, String> payload = Map.of("text", "*" + subject + "*\n" + body);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload, headers), Void.class);
        log.info("Slack notification sent for: {}", subject);
    }

    private void logEmail(Notification config, String subject, String body) {
        Map<String, Object> configMap = config.getConfig();
        String email = configMap != null ? (String) configMap.get("email") : null;
        // In production, integrate with JavaMailSender or SES
        log.info("EMAIL notification (not sent - no SMTP): to={}, subject={}, body={}", email, subject, body);
    }
}
