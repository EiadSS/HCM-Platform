package com.portfolio.hcm.security;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.portfolio.hcm.security.AuthDtos.AuthResponse;
import static com.portfolio.hcm.security.AuthDtos.LoginRequest;
import static com.portfolio.hcm.security.AuthDtos.MeResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password(), request.visitorId());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(currentUserService.requireUser());
    }
}
