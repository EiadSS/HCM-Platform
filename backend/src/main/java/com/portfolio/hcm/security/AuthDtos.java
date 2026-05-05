package com.portfolio.hcm.security;

import com.portfolio.hcm.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, MeResponse user) {
    }

    public record MeResponse(
            UUID userId,
            UUID tenantId,
            String email,
            String displayName,
            Set<UserRole> roles,
            boolean demoMode
    ) {
    }
}
