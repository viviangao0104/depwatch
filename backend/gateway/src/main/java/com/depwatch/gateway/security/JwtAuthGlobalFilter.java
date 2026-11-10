package com.depwatch.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
  * Global JWT verification.
 *
  * 1. Public paths (login / register / health) pass through
  * 2. Everything else takes the token from Authorization: Bearer; WebSocket handshakes use ?token= (the browser WS API can't set headers)
  * 3. Verify signature + expiry + issuer
  * 4. Forward sub / email as X-User-Id / X-User-Email (any client-supplied copies were stripped first)
 *
  * The user ID is also stored as an exchange attribute for the rate-limiter KeyResolver.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    public static final String ATTR_USER_ID = "depwatch.userId";

    private final JwtParser parser;
    private final SecurityProperties security;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(JwtProperties jwt, SecurityProperties security) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(jwt.issuer())
                .build();
        this.security = security;
    }

    @Override
    public int getOrder() {
        return -100;   // run before RequestRateLimiter so rate limiting can be per user
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();

        // Public or not, strip client-supplied identity headers first — downstream trusts them unconditionally
        ServerHttpRequest stripped = req.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                })
                .build();

        if (HttpMethod.OPTIONS.equals(req.getMethod()) || isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String token = extractToken(req);
        if (token == null) {
            return reject(exchange, "Missing bearer token");
        }
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rejected for {}: {}", path, e.getMessage());
            return reject(exchange, "Invalid or expired token");
        }

        String userId = claims.getSubject();
        exchange.getAttributes().put(ATTR_USER_ID, userId);
        ServerHttpRequest mutated = stripped.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", claims.get("email", String.class))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String path) {
        return security.publicPaths().stream().anyMatch(p -> matcher.match(p, path));
    }

    private static String extractToken(ServerHttpRequest req) {
        String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        if (req.getURI().getPath().startsWith("/ws")) {
            return req.getQueryParams().getFirst("token");
        }
        return null;
    }

    private static Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        DataBuffer buf = res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buf));
    }
}
