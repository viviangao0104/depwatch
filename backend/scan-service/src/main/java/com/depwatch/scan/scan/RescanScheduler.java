package com.depwatch.scan.scan;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持续监控：每天重新用最新的 OSV 数据检查每个项目。
 *
 * ShedLock：scan-service 跑多个副本时，@Scheduled 会在每个副本上触发；
 * @SchedulerLock 用 DB 里的 shedlock 表做分布式锁，保证同一时刻只有一个副本执行。
 * lockAtLeastFor 防止任务秒完后另一个时钟稍慢的副本又拿到锁再跑一遍。
 */
@Component
@ConditionalOnProperty(prefix = "depwatch.rescan", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RescanScheduler {

    private static final Logger log = LoggerFactory.getLogger(RescanScheduler.class);

    private final ScanRepository scans;
    private final ScanProcessor processor;

    public RescanScheduler(ScanRepository scans, ScanProcessor processor) {
        this.scans = scans;
        this.processor = processor;
    }

    @Scheduled(cron = "${depwatch.rescan.cron:0 0 3 * * *}")
    @SchedulerLock(name = "dailyRescan", lockAtMostFor = "${depwatch.rescan.lock-at-most-for:PT2H}", lockAtLeastFor = "PT5M")
    public void rescanAll() {
        List<Scan> latest = scans.findLatestCompletedPerProject();
        log.info("Daily rescan: {} projects", latest.size());
        for (Scan s : latest) {
            try {
                processor.rescan(s);
            } catch (Exception e) {
                log.error("Rescan failed for project {}", s.getProjectId(), e);
            }
        }
    }
}
