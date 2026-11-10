package com.depwatch.project.auth;

import com.depwatch.common.security.UserContext;
import com.depwatch.project.auth.AuthDtos.AuthResponse;
import com.depwatch.project.auth.AuthDtos.LoginRequest;
import com.depwatch.project.auth.AuthDtos.RegisterRequest;
import com.depwatch.project.auth.AuthDtos.UserInfo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /** Requires login (the gateway verifies the JWT and injects the user headers). */
    @GetMapping("/me")
    public UserInfo me() {
        return auth.me(UserContext.require().id());
    }
}
