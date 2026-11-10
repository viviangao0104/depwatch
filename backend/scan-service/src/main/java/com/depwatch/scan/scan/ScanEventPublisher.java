package com.depwatch.scan.scan;

import com.depwatch.common.events.ScanCompletedEvent;
import com.depwatch.common.events.ScanProgressEvent;
import com.depwatch.common.events.ScanStage;
import com.depwatch.common.events.Topics;
import com.depwatch.common.events.VulnerabilityDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
  * Outbound events from scan-service. Progress events are fire-and-forget (losing one is fine);
  * completed / detected events are also sent asynchronously with failures logged — they deliberately bypass the outbox:
  * the scan result is already in the DB and visible on refresh, and a missed alert is caught by the next scheduled rescan.
 */
@Component
public class ScanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScanEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public ScanEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void progress(Scan scan, ScanStage stage, int current, int total, String message) {
        send(Topics.SCAN_PROGRESS, scan.getId().toString(),
                ScanProgressEvent.of(scan.getId(), scan.getProjectId(), scan.getUserId(), stage, current, total, message));
    }

    public void completed(Scan s) {
        send(Topics.SCAN_COMPLETED, s.getProjectId().toString(), new ScanCompletedEvent(
                UUID.randomUUID(), s.getId(), s.getProjectId(), s.getProjectName(), s.getUserId(),
                s.getStatus().name(), s.getTrigger().name(),
                s.getTotalPackages(), s.getVulnerablePackages(),
                s.getCriticalCount(), s.getHighCount(), s.getMediumCount(), s.getLowCount(),
                s.getNewFindings(), s.getResolvedFindings(), s.getErrorMessage(), java.time.Instant.now()));
    }

    public void detected(VulnerabilityDetectedEvent event) {
        send(Topics.VULN_DETECTED, event.userId().toString(), event);
    }

    private void send(String topic, String key, Object event) {
        kafka.send(topic, key, event).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish {} to {}: {}", event.getClass().getSimpleName(), topic, ex.getMessage());
        });
    }
}
