package com.depwatch.scan.scan;

import com.depwatch.common.Severity;
import com.depwatch.common.security.UserContext;
import com.depwatch.common.web.ApiException;
import com.depwatch.scan.graph.DependencyGraph;
import com.depwatch.scan.scan.ScanDtos.DependencyPath;
import com.depwatch.scan.scan.ScanDtos.FindingView;
import com.depwatch.scan.scan.ScanDtos.ScanSummary;
import com.depwatch.scan.scan.ScanDtos.VulnerabilityView;
import com.depwatch.scan.vuln.VulnerabilityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScanQueryService {

    private final ScanRepository scans;
    private final VulnerabilityRepository vulns;
    private final ScanGraphStore graphStore;
    private final JdbcTemplate jdbc;

    public ScanQueryService(ScanRepository scans, VulnerabilityRepository vulns, ScanGraphStore graphStore, JdbcTemplate jdbc) {
        this.scans = scans;
        this.vulns = vulns;
        this.graphStore = graphStore;
        this.jdbc = jdbc;
    }

    public Page<ScanSummary> list(UUID projectId, int page, int size) {
        UUID user = UserContext.require().id();
        var pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Scan> result = projectId == null
                ? scans.findByUserIdOrderByStartedAtDesc(user, pageable)
                : scans.findByProjectIdAndUserIdOrderByStartedAtDesc(projectId, user, pageable);
        return result.map(ScanSummary::from);
    }

    public ScanSummary get(UUID id) {
        return ScanSummary.from(owned(id));
    }

    public List<FindingView> findings(UUID scanId, Severity minSeverity, Boolean onlyNew) {
        owned(scanId);
        StringBuilder sql = new StringBuilder("""
                SELECT d.node_id, d.ecosystem, d.name, d.version, d.direct,
                       v.id AS vuln_id, v.summary, v.severity, v.cvss_score, v.aliases,
                       f.fixed_version, f.is_new
                FROM scan_findings f
                JOIN scan_dependencies d ON d.scan_id = f.scan_id AND d.node_id = f.node_id
                JOIN vulnerabilities v ON v.id = f.vuln_id
                WHERE f.scan_id = ?
                """);
        if (Boolean.TRUE.equals(onlyNew)) sql.append(" AND f.is_new");
        // 严重度按 enum 顺序排序：CRITICAL > HIGH > MEDIUM > LOW > UNKNOWN
        sql.append("""
                 ORDER BY CASE v.severity
                              WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2
                              WHEN 'LOW' THEN 3 ELSE 4 END,
                          v.cvss_score DESC NULLS LAST, d.direct DESC, d.name
                """);
        List<FindingView> all = jdbc.query(sql.toString(), (rs, i) -> new FindingView(
                rs.getString("node_id"), rs.getString("ecosystem"), rs.getString("name"), rs.getString("version"),
                rs.getBoolean("direct"), rs.getString("vuln_id"), rs.getString("summary"),
                Severity.valueOf(rs.getString("severity")), rs.getBigDecimal("cvss_score"),
                rs.getString("fixed_version"), rs.getBoolean("is_new"), rs.getString("aliases")), scanId);
        if (minSeverity == null) return all;
        return all.stream().filter(f -> f.severity().atLeast(minSeverity)).toList();
    }

    public List<Map<String, Object>> dependencies(UUID scanId) {
        owned(scanId);
        return graphStore.listDependencies(scanId);
    }

    public DependencyPath path(UUID scanId, String nodeId) {
        owned(scanId);
        var edges = graphStore.loadEdges(scanId);
        return new DependencyPath(nodeId, DependencyGraph.shortestPath(edges, nodeId));
    }

    public VulnerabilityView vulnerability(String id) {
        return vulns.findById(id).map(VulnerabilityView::from).orElseThrow(() -> ApiException.notFound("Vulnerability"));
    }

    private Scan owned(UUID id) {
        return scans.findByIdAndUserId(id, UserContext.require().id()).orElseThrow(() -> ApiException.notFound("Scan"));
    }
}
