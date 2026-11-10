package com.depwatch.scan.scan;

import com.depwatch.common.events.ScanRequestedEvent;
import com.depwatch.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ScanRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ScanRequestedListener.class);

    private final ScanProcessor processor;

    public ScanRequestedListener(ScanProcessor processor) {
        this.processor = processor;
    }

    /**
      * concurrency=3 (see yml): up to 3 scans in parallel, matching the topic's 3 partitions.
      * Events for one project share a key → one partition → serial processing, so two scans of the same project never diff against each other mid-flight.
     */
    @KafkaListener(topics = Topics.SCAN_REQUESTED, groupId = "scan-service")
    public void onScanRequested(ScanRequestedEvent event) {
        log.info("Received ScanRequested {} for project {}", event.scanId(), event.projectId());
        processor.process(event);
    }
}
