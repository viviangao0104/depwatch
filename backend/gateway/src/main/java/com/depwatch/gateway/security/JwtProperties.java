package com.depwatch.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "depwatch.jwt")
public record JwtProperties(String secret, String issuer) {
}
