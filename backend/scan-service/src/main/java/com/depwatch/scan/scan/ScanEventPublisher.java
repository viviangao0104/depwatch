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
 * scan-service 的出站事件。进度事件是 fire-and-forget（丢了无所谓），
 * completed / detected 事件也异步发但记录失败日志——它们不走 outbox，是一个有意的取舍：
 * 扫描结果已经在 DB 里，用户刷新页面就能看到；告警漏发可以靠下次定时重扫补上。
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
