package com.depwatch.alert.ws;

import com.depwatch.alert.alert.Alert;
import com.depwatch.alert.alert.AlertDtos.AlertView;
import com.depwatch.common.events.ScanProgressEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LivePublisher {

    private final SimpMessagingTemplate ws;

    public LivePublisher(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void progress(ScanProgressEvent e) {
        ws.convertAndSend("/topic/scans/" + e.scanId(), e);
    }

    public void alert(UUID userId, Alert alert) {
        ws.convertAndSend("/topic/users/" + userId + "/alerts", AlertView.from(alert));
    }
}
