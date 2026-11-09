package com.depwatch.project.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "depwatch.jwt")
public record JwtProperties(String secret, int ttlHours, String issuer) {
}
