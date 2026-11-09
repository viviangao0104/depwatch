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
 * 纯 HTTP 层。三个 Resilience4j 注解的生效顺序（外→内）：Retry → CircuitBreaker → RateLimiter。
 *
 *  - RateLimiter：每秒 20 次，拿不到令牌等待而不是直接失败。OSV 是公共免费 API，要礼貌。
 *  - CircuitBreaker：连续失败率超过 50% 就熔断 30 秒，快速失败，不让线程都卡在等 OSV 超时上。
 *  - Retry：对 5xx / 网络异常指数退避重试 4 次；4xx（比如我们发的请求有问题）不重试。
 *
 * 配置都在 application.yml 的 resilience4j.* 下，运行时可通过 /actuator/circuitbreakers 观察状态。
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

    /** 返回原始 JSON 字符串，便于缓存和原样落库。404 返回 null。 */
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
