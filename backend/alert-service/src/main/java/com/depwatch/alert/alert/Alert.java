package com.depwatch.alert.alert;

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
@Table(name = "alerts")
public class Alert {

    public enum Kind { NEW_VULNERABILITY, SCAN_COMPLETED, SCAN_FAILED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "vuln_id")
    private String vulnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Alert() {
    }

    public Alert(UUID userId, UUID projectId, String projectName, UUID scanId, String vulnId,
                 Severity severity, Kind kind, String title, String message) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.projectId = projectId;
        this.projectName = projectName;
        this.scanId = scanId;
        this.vulnId = vulnId;
        this.severity = severity;
        this.kind = kind;
        this.title = title;
        this.message = message;
    }

    public void markRead() {
        if (readAt == null) readAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public UUID getScanId() { return scanId; }
    public String getVulnId() { return vulnId; }
    public Severity getSeverity() { return severity; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
