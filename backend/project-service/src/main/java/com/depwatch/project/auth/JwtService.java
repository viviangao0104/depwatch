package com.depwatch.project.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 签发 HS256 JWT。网关用同一个 secret 校验。
 * 生产里把 secret 放 AWS Secrets Manager / SSM Parameter Store，通过环境变量注入。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("depwatch.jwt.secret must be at least 32 bytes for HS256");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.ttlHours(), ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return props.ttlHours() * 3600L;
    }
}
