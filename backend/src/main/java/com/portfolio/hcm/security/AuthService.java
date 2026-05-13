package com.portfolio.hcm.security;

import com.portfolio.hcm.analytics.AnalyticsService;
import com.portfolio.hcm.common.ResourceNotFoundException;
import com.portfolio.hcm.tenant.TenantRepository;
import com.portfolio.hcm.user.AccountStatus;
import com.portfolio.hcm.user.UserAccount;
import com.portfolio.hcm.user.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.portfolio.hcm.security.AuthDtos.AuthResponse;
import static com.portfolio.hcm.security.AuthDtos.MeResponse;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AnalyticsService analyticsService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AnalyticsService analyticsService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.analyticsService = analyticsService;
    }

    @Transactional
    public AuthResponse login(String email, String password, String visitorId) {
        var user = userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (user.getStatus() != AccountStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        analyticsService.recordLogin(user, visitorId);
        return new AuthResponse(jwtService.issue(user), toMe(user));
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser authenticatedUser) {
        var user = userAccountRepository.findById(authenticatedUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
        return toMe(user);
    }

    private MeResponse toMe(UserAccount user) {
        var tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        return new MeResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles(),
                tenant.isDemoMode()
        );
    }
}
