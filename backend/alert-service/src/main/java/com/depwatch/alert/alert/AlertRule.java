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

/** 每个用户一条规则：低于 minSeverity 的漏洞不打扰。 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false, length = 10)
    private Severity minSeverity = Severity.LOW;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(name = "webhook_url", length = 1000)
    private String webhookUrl;

    @Column(name = "notify_on_scan_complete", nullable = false)
    private boolean notifyOnScanComplete = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AlertRule() {
    }

    public AlertRule(UUID userId) {
        this.userId = userId;
    }

    public static AlertRule defaults(UUID userId) {
        return new AlertRule(userId);
    }

    public void update(Severity minSeverity, boolean inApp, String webhookUrl, boolean notifyOnScanComplete) {
        this.minSeverity = minSeverity;
        this.inApp = inApp;
        this.webhookUrl = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim();
        this.notifyOnScanComplete = notifyOnScanComplete;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public Severity getMinSeverity() { return minSeverity; }
    public boolean isInApp() { return inApp; }
    public String getWebhookUrl() { return webhookUrl; }
    public boolean isNotifyOnScanComplete() { return notifyOnScanComplete; }
    public Instant getUpdatedAt() { return updatedAt; }
}
