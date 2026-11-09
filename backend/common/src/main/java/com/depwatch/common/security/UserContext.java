package com.depwatch.common.security;

import com.depwatch.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 请求级别的用户上下文（ThreadLocal）。
 * 网关已经校验过 JWT，并把用户信息放在 X-User-Id / X-User-Email 头里转发给下游。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    static void clear() {
        HOLDER.remove();
    }

    /** 当前用户；未登录时抛 401。 */
    public static UserPrincipal require() {
        UserPrincipal p = HOLDER.get();
        if (p == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return p;
    }

    public static UserPrincipal currentOrNull() {
        return HOLDER.get();
    }
}
