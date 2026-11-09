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
     * concurrency=3（见 yml）：最多 3 个线程并行扫描，对应 topic 的 3 个分区。
     * 同一个项目的事件 key 相同 → 同一分区 → 串行处理，避免同一项目两次扫描互相 diff 出错。
     */
    @KafkaListener(topics = Topics.SCAN_REQUESTED, groupId = "scan-service")
    public void onScanRequested(ScanRequestedEvent event) {
        log.info("Received ScanRequested {} for project {}", event.scanId(), event.projectId());
        processor.process(event);
    }
}
