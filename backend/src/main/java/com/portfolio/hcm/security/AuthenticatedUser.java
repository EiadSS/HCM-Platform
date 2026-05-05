package com.portfolio.hcm.security;

import com.portfolio.hcm.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        String email,
        Set<UserRole> roles
) {
    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }
}
