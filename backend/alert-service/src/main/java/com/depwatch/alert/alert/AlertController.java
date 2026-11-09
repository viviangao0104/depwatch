package com.depwatch.alert.alert;

import com.depwatch.alert.alert.AlertDtos.AlertView;
import com.depwatch.alert.alert.AlertDtos.RuleView;
import com.depwatch.alert.alert.AlertDtos.UnreadCount;
import com.depwatch.alert.alert.AlertDtos.UpdateRuleRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AlertView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.list(unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread() {
        return new UnreadCount(service.unreadCount());
    }

    @PostMapping("/{id}/read")
    public AlertView markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead());
    }

    @GetMapping("/rules")
    public RuleView rule() {
        return service.rule();
    }

    @PutMapping("/rules")
    public RuleView updateRule(@Valid @RequestBody UpdateRuleRequest req) {
        return service.updateRule(req);
    }
}
