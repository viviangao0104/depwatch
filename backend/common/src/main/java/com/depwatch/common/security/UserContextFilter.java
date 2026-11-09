package com.depwatch.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 从网关注入的头里恢复用户身份。
 *
 * 安全前提：下游服务只能被网关访问（私有子网 / 安全组 / K8s NetworkPolicy），
 * 且网关会剥离客户端自带的 X-User-* 头。这个权衡在 docs/ARCHITECTURE.md 第 3 节有说明。
 */
public class UserContextFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        try {
            if (userId != null && !userId.isBlank()) {
                UserContext.set(new UserPrincipal(UUID.fromString(userId), request.getHeader(HEADER_USER_EMAIL)));
            }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed " + HEADER_USER_ID);
        } finally {
            UserContext.clear();
        }
    }
}
