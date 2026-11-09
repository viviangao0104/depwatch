package com.depwatch.scan.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** 一个 (依赖节点, 漏洞) 组合 = 一条 finding。 */
@Entity
@Table(name = "scan_findings")
public class ScanFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "vuln_id", nullable = false)
    private String vulnId;

    @Column(name = "fixed_version")
    private String fixedVersion;

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    protected ScanFinding() {
    }

    public ScanFinding(UUID scanId, String nodeId, String vulnId, String fixedVersion, boolean isNew) {
        this.scanId = scanId;
        this.nodeId = nodeId;
        this.vulnId = vulnId;
        this.fixedVersion = fixedVersion;
        this.isNew = isNew;
    }

    public Long getId() { return id; }
    public UUID getScanId() { return scanId; }
    public String getNodeId() { return nodeId; }
    public String getVulnId() { return vulnId; }
    public String getFixedVersion() { return fixedVersion; }
    public boolean isNew() { return isNew; }

    /** 用于 diff 的身份键：同一个包版本上的同一个漏洞。 */
    public String key() {
        return nodeId + "|" + vulnId;
    }
}
