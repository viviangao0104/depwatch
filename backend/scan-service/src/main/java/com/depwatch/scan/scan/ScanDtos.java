package com.depwatch.scan.scan;

import com.depwatch.common.Severity;
import com.depwatch.scan.vuln.Vulnerability;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ScanDtos {

    private ScanDtos() {
    }

    public record ScanSummary(
            UUID id, UUID projectId, String projectName, String trigger, String status,
            String manifestFileName,
            int totalPackages, int directPackages, int vulnerablePackages,
            int critical, int high, int medium, int low, int unknown,
            int newFindings, int resolvedFindings,
            String errorMessage, Instant startedAt, Instant completedAt
    ) {
        public static ScanSummary from(Scan s) {
            return new ScanSummary(s.getId(), s.getProjectId(), s.getProjectName(), s.getTrigger().name(),
                    s.getStatus().name(), s.getManifestFileName(),
                    s.getTotalPackages(), s.getDirectPackages(), s.getVulnerablePackages(),
                    s.getCriticalCount(), s.getHighCount(), s.getMediumCount(), s.getLowCount(), s.getUnknownCount(),
                    s.getNewFindings(), s.getResolvedFindings(),
                    s.getErrorMessage(), s.getStartedAt(), s.getCompletedAt());
        }
    }

    public record FindingView(
            String nodeId, String ecosystem, String packageName, String version, boolean direct,
            String vulnId, String summary, Severity severity, BigDecimal cvssScore,
            String fixedVersion, boolean isNew,
            @JsonRawValue String aliases
    ) {
    }

    public record VulnerabilityView(
            String id, String summary, String details, Severity severity,
            BigDecimal cvssScore, String cvssVector,
            @JsonRawValue String aliases, @JsonRawValue String references,
            Instant publishedAt, Instant modifiedAt
    ) {
        public static VulnerabilityView from(Vulnerability v) {
            return new VulnerabilityView(v.getId(), v.getSummary(), v.getDetails(), v.getSeverity(),
                    v.getCvssScore(), v.getCvssVector(), v.getAliases(), v.getReferences(),
                    v.getPublishedAt(), v.getModifiedAt());
        }
    }

    /** "这个包是怎么进到我项目里的" */
    public record DependencyPath(String nodeId, List<String> path) {
    }
}
