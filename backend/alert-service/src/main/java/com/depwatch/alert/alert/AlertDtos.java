package com.depwatch.alert.alert;

import com.depwatch.common.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertView(UUID id, UUID projectId, String projectName, UUID scanId, String vulnId,
                            Severity severity, String kind, String title, String message,
                            Instant createdAt, Instant readAt) {
        public static AlertView from(Alert a) {
            return new AlertView(a.getId(), a.getProjectId(), a.getProjectName(), a.getScanId(), a.getVulnId(),
                    a.getSeverity(), a.getKind().name(), a.getTitle(), a.getMessage(), a.getCreatedAt(), a.getReadAt());
        }
    }

    public record RuleView(Severity minSeverity, boolean inApp, String webhookUrl, boolean notifyOnScanComplete) {
        public static RuleView from(AlertRule r) {
            return new RuleView(r.getMinSeverity(), r.isInApp(), r.getWebhookUrl(), r.isNotifyOnScanComplete());
        }
    }

    public record UpdateRuleRequest(@NotNull Severity minSeverity, boolean inApp,
                                    @Size(max = 1000) String webhookUrl, boolean notifyOnScanComplete) {
    }

    public record UnreadCount(long unread) {
    }
}
