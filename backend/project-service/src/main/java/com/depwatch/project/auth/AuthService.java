package com.depwatch.project.auth;

import com.depwatch.common.web.ApiException;
import com.depwatch.project.auth.AuthDtos.AuthResponse;
import com.depwatch.project.auth.AuthDtos.LoginRequest;
import com.depwatch.project.auth.AuthDtos.RegisterRequest;
import com.depwatch.project.auth.AuthDtos.UserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AuthService(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email already registered");
        }
        User user = users.save(new User(req.email().toLowerCase(), encoder.encode(req.password()), req.displayName()));
        return new AuthResponse(jwt.issue(user), jwt.ttlSeconds(), UserInfo.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                // Same message either way — don't reveal whether the email exists or the password was wrong
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new AuthResponse(jwt.issue(user), jwt.ttlSeconds(), UserInfo.from(user));
    }

    @Transactional(readOnly = true)
    public UserInfo me(UUID id) {
        return users.findById(id).map(UserInfo::from).orElseThrow(() -> ApiException.notFound("User"));
    }
}
