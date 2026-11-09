package com.depwatch.scan.scan;

import com.depwatch.common.events.ScanRequestedEvent;
import com.depwatch.common.events.ScanStage;
import com.depwatch.common.events.VulnerabilityDetectedEvent;
import com.depwatch.common.storage.ManifestStorage;
import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.graph.DependencyNode;
import com.depwatch.scan.osv.OsvClient;
import com.depwatch.scan.parser.ManifestParser;
import com.depwatch.scan.parser.ManifestParserRegistry;
import com.depwatch.scan.vuln.OsvVulnerabilityMapper;
import com.depwatch.scan.vuln.Vulnerability;
import com.depwatch.scan.vuln.VulnerabilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 扫描流水线：PARSING → QUERYING → ENRICHING → DIFFING → COMPLETED。
 *
 * 事务边界刻意切得很细（TransactionTemplate 而不是整个方法 @Transactional）：
 * 调 OSV 可能要几十秒甚至几分钟，不能让一个 DB 连接在这期间一直被占着。
 * 每个阶段落库时才开事务，中间状态靠 scans.status = RUNNING 表达。
 */
@Service
public class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);

    private final ScanRepository scans;
    private final ScanGraphStore graphStore;
    private final ScanFindingRepository findings;
    private final VulnerabilityRepository vulns;
    private final OsvClient osv;
    private final OsvVulnerabilityMapper osvMapper;
    private final ManifestParserRegistry parsers;
    private final ManifestStorage storage;
    private final ScanEventPublisher events;
    private final TransactionTemplate tx;

    public ScanProcessor(ScanRepository scans, ScanGraphStore graphStore, ScanFindingRepository findings,
                         VulnerabilityRepository vulns, OsvClient osv, OsvVulnerabilityMapper osvMapper,
                         ManifestParserRegistry parsers, ManifestStorage storage,
                         ScanEventPublisher events, TransactionTemplate tx) {
        this.scans = scans;
        this.graphStore = graphStore;
        this.findings = findings;
        this.vulns = vulns;
        this.osv = osv;
        this.osvMapper = osvMapper;
        this.parsers = parsers;
        this.storage = storage;
        this.events = events;
        this.tx = tx;
    }

    // ───────────────────────── 入口 1：用户上传触发 ─────────────────────────

    public void process(ScanRequestedEvent ev) {
        // 幂等：Kafka 重复投递 / 消费者重启后重放，同一个 scanId 只处理一次
        Optional<Scan> existing = scans.findById(ev.scanId());
        if (existing.isPresent() && existing.get().getStatus() != Scan.Status.FAILED) {
            log.info("Scan {} already {} — skipping duplicate event", ev.scanId(), existing.get().getStatus());
            return;
        }
        existing.ifPresent(s -> tx.executeWithoutResult(st -> scans.delete(s)));   // 失败过的允许重跑

        Scan scan = new Scan(ev.scanId(), ev.projectId(), ev.projectName(), ev.userId(),
                ev.manifestKey(), ev.manifestFileName(), Scan.Trigger.MANUAL);
        tx.executeWithoutResult(st -> scans.save(scan));
        log.info("Scan {} started for project {} ({})", scan.getId(), scan.getProjectId(), ev.manifestFileName());

        try {
            events.progress(scan, ScanStage.PARSING, 0, 0, "Parsing " + ev.manifestFileName());
            DependencyGraph graph = parse(ev.manifestKey(), ev.manifestFileName());
            tx.executeWithoutResult(st -> graphStore.saveGraph(scan.getId(), graph));
            graph.warnings().forEach(w -> log.info("Scan {} parser warning: {}", scan.getId(), w));

            List<DependencyNode> nodes = new ArrayList<>(graph.nodes());
            Set<String> direct = nodes.stream().map(DependencyNode::id).filter(graph::isDirect).collect(Collectors.toSet());
            analyze(scan, nodes, direct);
        } catch (Exception e) {
            fail(scan, e);
        }
    }

    // ───────────────────────── 入口 2：定时重扫 ─────────────────────────

    public void rescan(Scan previous) {
        Scan scan = new Scan(UUID.randomUUID(), previous.getProjectId(), previous.getProjectName(), previous.getUserId(),
                previous.getManifestKey(), previous.getManifestFileName(), Scan.Trigger.SCHEDULED);
        tx.executeWithoutResult(st -> {
            scans.save(scan);
            graphStore.copyGraph(previous.getId(), scan.getId());     // 复用上次的依赖图，不重新解析
        });
        log.info("Scheduled rescan {} for project {} (based on {})", scan.getId(), scan.getProjectId(), previous.getId());
        try {
            List<DependencyNode> nodes = graphStore.loadNodes(scan.getId());
            Set<String> direct = graphStore.loadDirectNodeIds(scan.getId());
            analyze(scan, nodes, direct);
        } catch (Exception e) {
            fail(scan, e);
        }
    }

    // ───────────────────────── 共用流水线 ─────────────────────────

    private void analyze(Scan scan, List<DependencyNode> nodes, Set<String> directIds) {
        Map<String, DependencyNode> byId = new HashMap<>();
        nodes.forEach(n -> byId.put(n.id(), n));

        // QUERYING：批量问 OSV "这些 (包, 版本) 有哪些漏洞"
        events.progress(scan, ScanStage.QUERYING, 0, nodes.size(), "Querying OSV for " + nodes.size() + " packages");
        Map<String, List<String>> hits = osv.findVulnerabilities(nodes,
                done -> events.progress(scan, ScanStage.QUERYING, done, nodes.size(), null));

        // ENRICHING：拉每个唯一漏洞的详情（Redis 缓存挡住重复请求）
        Set<String> vulnIds = hits.values().stream().flatMap(List::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        events.progress(scan, ScanStage.ENRICHING, 0, vulnIds.size(), "Fetching details for " + vulnIds.size() + " vulnerabilities");
        Map<String, JsonNode> osvJson = new HashMap<>();
        Map<String, Vulnerability> entities = new HashMap<>();
        int i = 0;
        for (String id : vulnIds) {
            String raw = osv.fetchVulnerabilityRaw(id);
            if (raw != null) {
                JsonNode json = osvMapper.parse(raw);
                osvJson.put(id, json);
                entities.put(id, osvMapper.toEntity(id, json, raw));
            }
            if (++i % 10 == 0 || i == vulnIds.size()) {
                events.progress(scan, ScanStage.ENRICHING, i, vulnIds.size(), null);
            }
        }
        tx.executeWithoutResult(st -> vulns.saveAll(entities.values()));

        // DIFFING：与该项目上一次成功扫描比对
        events.progress(scan, ScanStage.DIFFING, 0, 0, "Comparing with previous scan");
        Set<String> previousKeys = scans
                .findFirstByProjectIdAndStatusAndIdNotOrderByStartedAtDesc(scan.getProjectId(), Scan.Status.COMPLETED, scan.getId())
                .map(prev -> findings.findByScanId(prev.getId()).stream().map(ScanFinding::key).collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        boolean firstScan = previousKeys.isEmpty();

        List<ScanFinding> current = new ArrayList<>();
        Set<String> currentKeys = new HashSet<>();
        for (Map.Entry<String, List<String>> e : hits.entrySet()) {
            DependencyNode node = byId.get(e.getKey());
            for (String vulnId : e.getValue()) {
                if (!entities.containsKey(vulnId)) continue;
                String fixed = osvMapper.fixedVersionFor(osvJson.get(vulnId), node);
                ScanFinding f = new ScanFinding(scan.getId(), node.id(), vulnId, fixed, !previousKeys.contains(node.id() + "|" + vulnId));
                current.add(f);
                currentKeys.add(f.key());
                scan.countSeverity(entities.get(vulnId).getSeverity());
            }
        }
        int resolved = (int) previousKeys.stream().filter(k -> !currentKeys.contains(k)).count();
        int newCount = (int) current.stream().filter(ScanFinding::isNew).count();

        scan.complete(nodes.size(), directIds.size(), hits.size(), newCount, resolved);
        tx.executeWithoutResult(st -> {
            findings.saveAll(current);
            scans.save(scan);
        });

        events.progress(scan, ScanStage.COMPLETED, nodes.size(), nodes.size(),
                "%d vulnerable packages, %d findings (%d new)".formatted(hits.size(), current.size(), newCount));
        events.completed(scan);

        // 每条新增 finding 一个告警事件。首次扫描所有 finding 都是"新"，照发——用户确实第一次知道
        for (ScanFinding f : current) {
            if (!f.isNew()) continue;
            DependencyNode node = byId.get(f.getNodeId());
            Vulnerability v = entities.get(f.getVulnId());
            events.detected(new VulnerabilityDetectedEvent(UUID.randomUUID(), scan.getId(), scan.getProjectId(),
                    scan.getProjectName(), scan.getUserId(), v.getId(), node.ecosystem(), node.name(), node.version(),
                    v.getSeverity(), f.getFixedVersion(), v.getSummary(), directIds.contains(node.id()), Instant.now()));
        }
        log.info("Scan {} completed: {} packages, {} vulnerable, {} findings ({} new, {} resolved){}",
                scan.getId(), nodes.size(), hits.size(), current.size(), newCount, resolved, firstScan ? " [first scan]" : "");
    }

    private DependencyGraph parse(String key, String fileName) throws Exception {
        ManifestParser parser = parsers.forFile(fileName);
        try (InputStream in = storage.load(key)) {
            return parser.parse(in);
        }
    }

    private void fail(Scan scan, Exception e) {
        log.error("Scan {} failed", scan.getId(), e);
        scan.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        tx.executeWithoutResult(st -> scans.save(scan));
        events.progress(scan, ScanStage.FAILED, 0, 0, scan.getErrorMessage());
        events.completed(scan);
    }
}
