package com.depwatch.alert.notify;

import com.depwatch.alert.alert.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
  * Slack-compatible Incoming Webhook (Discord / Feishu / DingTalk differ slightly; extend here).
  * @Async: an outbound HTTP call must not block the Kafka consumer thread.
 *
  * SSRF protection: user-supplied URLs must be https and must not point at private networks — otherwise an
  * attacker could make this service call 169.254.169.254 (cloud metadata endpoint) and the like.
 */
@Component
public class WebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private final RestClient http;

    public WebhookNotifier(@Value("${depwatch.webhook.connect-timeout-ms:3000}") int connectTimeout,
                           @Value("${depwatch.webhook.read-timeout-ms:5000}") int readTimeout) {
        this.http = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofMillis(connectTimeout))
                        .withReadTimeout(Duration.ofMillis(readTimeout))))
                .build();
    }

    @Async
    public void send(String webhookUrl, Alert alert) {
        if (!isSafeUrl(webhookUrl)) {
            log.warn("Refusing to call unsafe webhook URL for user {}", alert.getUserId());
            return;
        }
        String emoji = switch (alert.getSeverity()) {
            case CRITICAL -> ":rotating_light:";
            case HIGH -> ":red_circle:";
            case MEDIUM -> ":large_orange_circle:";
            case LOW -> ":large_yellow_circle:";
            default -> ":white_circle:";
        };
        Map<String, Object> payload = Map.of(
                "text", emoji + " *" + alert.getTitle() + "*",
                "blocks", List.of(
                        Map.of("type", "section", "text", Map.of("type", "mrkdwn",
                                "text", emoji + " *" + alert.getTitle() + "*\n" + (alert.getMessage() == null ? "" : alert.getMessage()))),
                        Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn",
                                "text", "Project: " + alert.getProjectName() + " · Severity: " + alert.getSeverity()
                                        + (alert.getVulnId() != null ? " · <https://osv.dev/vulnerability/" + alert.getVulnId() + "|" + alert.getVulnId() + ">" : ""))))
                ));
        try {
            http.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("Webhook delivery failed for alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    static boolean isSafeUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            String h = host.toLowerCase();
            if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".internal")) return false;
            if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("127.") || h.startsWith("169.254.")) return false;
            if (h.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
