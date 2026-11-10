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
  * Restores the user identity from the headers injected by the gateway.
 *
  * Security precondition: downstream services are reachable only through the gateway (private subnet / security groups / K8s NetworkPolicy),
  * and the gateway strips client-supplied X-User-* headers. The trade-off is discussed in docs/ARCHITECTURE.md §3.
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
