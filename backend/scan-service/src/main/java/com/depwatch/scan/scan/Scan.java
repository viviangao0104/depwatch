package com.depwatch.scan.scan;

import com.depwatch.common.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scans")
public class Scan {

    public enum Status { RUNNING, COMPLETED, FAILED }

    public enum Trigger { MANUAL, SCHEDULED }

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "manifest_key")
    private String manifestKey;

    @Column(name = "manifest_file_name")
    private String manifestFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RUNNING;

    @Column(name = "total_packages", nullable = false)
    private int totalPackages;
    @Column(name = "direct_packages", nullable = false)
    private int directPackages;
    @Column(name = "vulnerable_packages", nullable = false)
    private int vulnerablePackages;
    @Column(name = "critical_count", nullable = false)
    private int criticalCount;
    @Column(name = "high_count", nullable = false)
    private int highCount;
    @Column(name = "medium_count", nullable = false)
    private int mediumCount;
    @Column(name = "low_count", nullable = false)
    private int lowCount;
    @Column(name = "unknown_count", nullable = false)
    private int unknownCount;
    @Column(name = "new_findings", nullable = false)
    private int newFindings;
    @Column(name = "resolved_findings", nullable = false)
    private int resolvedFindings;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Scan() {
    }

    public Scan(UUID id, UUID projectId, String projectName, UUID userId,
                String manifestKey, String manifestFileName, Trigger trigger) {
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.userId = userId;
        this.manifestKey = manifestKey;
        this.manifestFileName = manifestFileName;
        this.trigger = trigger;
    }

    public void countSeverity(Severity s) {
        switch (s) {
            case CRITICAL -> criticalCount++;
            case HIGH -> highCount++;
            case MEDIUM -> mediumCount++;
            case LOW -> lowCount++;
            default -> unknownCount++;
        }
    }

    public void complete(int total, int direct, int vulnerable, int newFindings, int resolved) {
        this.totalPackages = total;
        this.directPackages = direct;
        this.vulnerablePackages = vulnerable;
        this.newFindings = newFindings;
        this.resolvedFindings = resolved;
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message == null ? "Unknown error" : message.substring(0, Math.min(message.length(), 2000));
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public UUID getUserId() { return userId; }
    public String getManifestKey() { return manifestKey; }
    public String getManifestFileName() { return manifestFileName; }
    public Trigger getTrigger() { return trigger; }
    public Status getStatus() { return status; }
    public int getTotalPackages() { return totalPackages; }
    public int getDirectPackages() { return directPackages; }
    public int getVulnerablePackages() { return vulnerablePackages; }
    public int getCriticalCount() { return criticalCount; }
    public int getHighCount() { return highCount; }
    public int getMediumCount() { return mediumCount; }
    public int getLowCount() { return lowCount; }
    public int getUnknownCount() { return unknownCount; }
    public int getNewFindings() { return newFindings; }
    public int getResolvedFindings() { return resolvedFindings; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
