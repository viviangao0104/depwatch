package com.depwatch.project.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(String accessToken, long expiresInSeconds, UserInfo user) {
    }

    public record UserInfo(UUID id, String email, String displayName) {
        static UserInfo from(User u) {
            return new UserInfo(u.getId(), u.getEmail(), u.getDisplayName());
        }
    }
}
