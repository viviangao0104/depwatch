package com.depwatch.alert.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
}
