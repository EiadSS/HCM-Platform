package com.portfolio.hcm.security;

import com.portfolio.hcm.common.ForbiddenOperationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {
    public AuthenticatedUser requireUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        return user;
    }

    public UUID tenantId() {
        return requireUser().tenantId();
    }
}
