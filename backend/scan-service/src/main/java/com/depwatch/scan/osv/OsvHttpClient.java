package com.depwatch.scan.osv;

import com.depwatch.scan.osv.OsvDtos.QueryBatchRequest;
import com.depwatch.scan.osv.OsvDtos.QueryBatchResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
  * Pure HTTP layer. The three Resilience4j annotations apply outermost → innermost: Retry → CircuitBreaker → RateLimiter.
 *
  *  - RateLimiter: 20 req/s; wait for a permit instead of failing. OSV is a free public API — be polite.
  *  - CircuitBreaker: open for 30s once the failure rate exceeds 50%; fail fast instead of parking every thread on an OSV timeout.
  *  - Retry: up to 4 attempts with exponential backoff on 5xx / network errors; 4xx (our request is wrong) is not retried.
 *
  * All settings live under resilience4j.* in application.yml; state is observable at /actuator/circuitbreakers.
 */
@Component
public class OsvHttpClient {

    private static final Logger log = LoggerFactory.getLogger(OsvHttpClient.class);
    static final String BACKEND = "osv";

    private final RestClient http;

    public OsvHttpClient(OsvProperties props) {
        var factory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.readTimeoutMs())));
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("User-Agent", "DepWatch/0.1 (+https://github.com/your-name/depwatch)")
                .build();
    }

    @Retry(name = BACKEND)
    @CircuitBreaker(name = BACKEND)
    @RateLimiter(name = BACKEND)
    public QueryBatchResponse queryBatch(QueryBatchRequest request) {
        log.debug("OSV querybatch: {} queries", request.queries().size());
        return http.post()
                .uri("/v1/querybatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(QueryBatchResponse.class);
    }

    /** Returns the raw JSON string for caching and verbatim persistence. Returns null on 404. */
    @Retry(name = BACKEND)
    @CircuitBreaker(name = BACKEND)
    @RateLimiter(name = BACKEND)
    public String fetchVulnerabilityRaw(String vulnId) {
        try {
            return http.get()
                    .uri("/v1/vulns/{id}", vulnId)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("OSV vuln {} not found", vulnId);
            return null;
        }
    }
}
