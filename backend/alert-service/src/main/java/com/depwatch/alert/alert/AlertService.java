package com.depwatch.alert.alert;

import com.depwatch.alert.alert.AlertDtos.AlertView;
import com.depwatch.alert.alert.AlertDtos.RuleView;
import com.depwatch.alert.alert.AlertDtos.UpdateRuleRequest;
import com.depwatch.alert.idempotency.IdempotencyGuard;
import com.depwatch.alert.notify.WebhookNotifier;
import com.depwatch.alert.ws.LivePublisher;
import com.depwatch.common.Severity;
import com.depwatch.common.events.ScanCompletedEvent;
import com.depwatch.common.events.VulnerabilityDetectedEvent;
import com.depwatch.common.security.UserContext;
import com.depwatch.common.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository alerts;
    private final AlertRuleRepository rules;
    private final IdempotencyGuard idempotency;
    private final LivePublisher live;
    private final WebhookNotifier webhook;

    public AlertService(AlertRepository alerts, AlertRuleRepository rules, IdempotencyGuard idempotency,
                        LivePublisher live, WebhookNotifier webhook) {
        this.alerts = alerts;
        this.rules = rules;
        this.idempotency = idempotency;
        this.live = live;
        this.webhook = webhook;
    }

    // ───────────── 事件入口 ─────────────

    @Transactional
    public void onVulnerabilityDetected(VulnerabilityDetectedEvent e) {
        if (!idempotency.markProcessed(e.eventId(), "VulnerabilityDetected")) return;

        AlertRule rule = rules.findById(e.userId()).orElseGet(() -> AlertRule.defaults(e.userId()));
        if (!e.severity().atLeast(rule.getMinSeverity())) return;      // 低于用户阈值，不打扰

        String title = "%s %s in %s@%s".formatted(e.severity(), e.vulnId(), e.packageName(), e.version());
        String message = (e.summary() == null ? "" : e.summary())
                + (e.fixedVersion() != null ? "\nFixed in " + e.fixedVersion() : "\nNo fix available yet")
                + (e.direct() ? "\nDirect dependency" : "\nTransitive dependency");
        Alert alert = alerts.save(new Alert(e.userId(), e.projectId(), e.projectName(), e.scanId(), e.vulnId(),
                e.severity(), Alert.Kind.NEW_VULNERABILITY, title, message));

        deliver(rule, alert);
    }

    @Transactional
    public void onScanCompleted(ScanCompletedEvent e) {
        if (!idempotency.markProcessed(e.eventId(), "ScanCompleted")) return;

        AlertRule rule = rules.findById(e.userId()).orElseGet(() -> AlertRule.defaults(e.userId()));
        boolean failed = "FAILED".equals(e.status());
        if (!failed && !rule.isNotifyOnScanComplete()) return;
        // 定时重扫没有新发现时不打扰用户
        if (!failed && "SCHEDULED".equals(e.trigger()) && e.newFindings() == 0) return;

        Severity sev = failed ? Severity.UNKNOWN
                : e.critical() > 0 ? Severity.CRITICAL
                : e.high() > 0 ? Severity.HIGH
                : e.medium() > 0 ? Severity.MEDIUM
                : e.low() > 0 ? Severity.LOW : Severity.UNKNOWN;
        String title = failed
                ? "Scan failed for " + e.projectName()
                : "Scan of %s: %d vulnerable / %d packages".formatted(e.projectName(), e.vulnerablePackages(), e.totalPackages());
        String message = failed ? e.errorMessage()
                : "%d critical, %d high, %d medium, %d low · %d new, %d resolved"
                .formatted(e.critical(), e.high(), e.medium(), e.low(), e.newFindings(), e.resolvedFindings());
        Alert alert = alerts.save(new Alert(e.userId(), e.projectId(), e.projectName(), e.scanId(), null, sev,
                failed ? Alert.Kind.SCAN_FAILED : Alert.Kind.SCAN_COMPLETED, title, message));

        deliver(rule, alert);
    }

    /**
     * 推送放在事务提交之后：如果先推了再回滚，用户会看到一条 DB 里不存在的告警。
     */
    private void deliver(AlertRule rule, Alert alert) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (rule.isInApp()) live.alert(alert.getUserId(), alert);
                if (rule.getWebhookUrl() != null) webhook.send(rule.getWebhookUrl(), alert);
            }
        });
    }

    // ───────────── REST ─────────────

    @Transactional(readOnly = true)
    public Page<AlertView> list(boolean unreadOnly, int page, int size) {
        UUID user = UserContext.require().id();
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return (unreadOnly
                ? alerts.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user, pageable)
                : alerts.findByUserIdOrderByCreatedAtDesc(user, pageable))
                .map(AlertView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return alerts.countByUserIdAndReadAtIsNull(UserContext.require().id());
    }

    @Transactional
    public AlertView markRead(UUID id) {
        Alert a = alerts.findByIdAndUserId(id, UserContext.require().id()).orElseThrow(() -> ApiException.notFound("Alert"));
        a.markRead();
        return AlertView.from(a);
    }

    @Transactional
    public int markAllRead() {
        return alerts.markAllRead(UserContext.require().id(), Instant.now());
    }

    @Transactional(readOnly = true)
    public RuleView rule() {
        UUID user = UserContext.require().id();
        return RuleView.from(rules.findById(user).orElseGet(() -> AlertRule.defaults(user)));
    }

    @Transactional
    public RuleView updateRule(UpdateRuleRequest req) {
        UUID user = UserContext.require().id();
        AlertRule rule = rules.findById(user).orElseGet(() -> new AlertRule(user));
        rule.update(req.minSeverity(), req.inApp(), req.webhookUrl(), req.notifyOnScanComplete());
        return RuleView.from(rules.save(rule));
    }
}
