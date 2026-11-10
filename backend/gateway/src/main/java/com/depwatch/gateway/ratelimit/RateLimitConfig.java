package com.depwatch.gateway.ratelimit;

import com.depwatch.gateway.security.JwtAuthGlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /**
      * Rate-limit key: user ID when authenticated, client IP otherwise (login / register endpoints).
      * Behind an ALB / CloudFront the real IP is the first entry of X-Forwarded-For.
     */
    @Bean
    public KeyResolver principalKeyResolver() {
        return exchange -> {
            Object userId = exchange.getAttribute(JwtAuthGlobalFilter.ATTR_USER_ID);
            if (userId != null) return Mono.just("user:" + userId);

            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            String ip = xff != null && !xff.isBlank()
                    ? xff.split(",")[0].trim()
                    : Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getAddress)
                    .map(a -> a.getHostAddress())
                    .orElse("unknown");
            return Mono.just("ip:" + ip);
        };
    }
}
