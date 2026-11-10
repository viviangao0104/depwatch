package com.depwatch.common.security;

import com.depwatch.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
  * Request-scoped user context (ThreadLocal).
  * The gateway has already verified the JWT and forwards the user in the X-User-Id / X-User-Email headers.
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

    /** Current user; throws 401 when not authenticated. */
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
