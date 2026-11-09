package com.depwatch.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "depwatch.security")
public record SecurityProperties(List<String> publicPaths) {
}
