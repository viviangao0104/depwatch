package com.depwatch.scan.scan;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
  * Continuous monitoring: re-check every project against the latest OSV data daily.
 *
  * ShedLock: with several scan-service replicas, @Scheduled fires on each of them;
  * @SchedulerLock takes a distributed lock in the shedlock table so only one replica runs at a time.
  * lockAtLeastFor stops a replica with a slightly slow clock from grabbing the lock and running again right after a fast finish.
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
