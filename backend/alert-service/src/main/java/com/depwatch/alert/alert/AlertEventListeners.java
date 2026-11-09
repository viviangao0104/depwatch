package com.depwatch.alert.alert;

import com.depwatch.alert.ws.LivePublisher;
import com.depwatch.common.events.ScanCompletedEvent;
import com.depwatch.common.events.ScanProgressEvent;
import com.depwatch.common.events.Topics;
import com.depwatch.common.events.VulnerabilityDetectedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventListeners {

    private final AlertService alerts;
    private final LivePublisher live;

    public AlertEventListeners(AlertService alerts, LivePublisher live) {
        this.alerts = alerts;
        this.live = live;
    }

    /** 进度事件：直接转发到 WebSocket，不落库、不去重（丢了也无所谓）。 */
    @KafkaListener(topics = Topics.SCAN_PROGRESS, groupId = "alert-service")
    public void onProgress(ScanProgressEvent e) {
        live.progress(e);
    }

    @KafkaListener(topics = Topics.VULN_DETECTED, groupId = "alert-service")
    public void onVulnerabilityDetected(VulnerabilityDetectedEvent e) {
        alerts.onVulnerabilityDetected(e);
    }

    @KafkaListener(topics = Topics.SCAN_COMPLETED, groupId = "alert-service")
    public void onScanCompleted(ScanCompletedEvent e) {
        alerts.onScanCompleted(e);
    }
}
