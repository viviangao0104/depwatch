package com.depwatch.scan.osv;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "depwatch.osv")
public record OsvProperties(String baseUrl, int batchSize, int connectTimeoutMs, int readTimeoutMs) {
}
