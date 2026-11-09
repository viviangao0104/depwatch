package com.depwatch.common.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String email) {
}
