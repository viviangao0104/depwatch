package com.depwatch.scan.scan;

import com.depwatch.common.Severity;
import com.depwatch.scan.scan.ScanDtos.DependencyPath;
import com.depwatch.scan.scan.ScanDtos.FindingView;
import com.depwatch.scan.scan.ScanDtos.ScanSummary;
import com.depwatch.scan.scan.ScanDtos.VulnerabilityView;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanQueryService query;

    public ScanController(ScanQueryService query) {
        this.query = query;
    }

    @GetMapping("/scans")
    public Page<ScanSummary> list(@RequestParam(required = false) UUID projectId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return query.list(projectId, page, size);
    }

    @GetMapping("/scans/{id}")
    public ScanSummary get(@PathVariable UUID id) {
        return query.get(id);
    }

    @GetMapping("/scans/{id}/findings")
    public List<FindingView> findings(@PathVariable UUID id,
                                      @RequestParam(required = false) Severity minSeverity,
                                      @RequestParam(required = false) Boolean onlyNew) {
        return query.findings(id, minSeverity, onlyNew);
    }

    @GetMapping("/scans/{id}/dependencies")
    public List<Map<String, Object>> dependencies(@PathVariable UUID id) {
        return query.dependencies(id);
    }

    /** nodeId 形如 npm:lodash@4.17.15，含特殊字符，放 query 参数而不是路径。 */
    @GetMapping("/scans/{id}/path")
    public DependencyPath path(@PathVariable UUID id, @RequestParam String nodeId) {
        return query.path(id, nodeId);
    }

    @GetMapping("/vulns/{id}")
    public VulnerabilityView vulnerability(@PathVariable String id) {
        return query.vulnerability(id);
    }
}
