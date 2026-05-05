package com.portfolio.hcm.demo;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.tenant.TenantRepository;
import com.portfolio.hcm.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final DemoDataSeeder demoDataSeeder;
    private final DemoResetPolicy demoResetPolicy;
    private final CurrentUserService currentUserService;
    private final TenantRepository tenantRepository;
    private final String resetSecret;

    public DemoController(
            DemoDataSeeder demoDataSeeder,
            DemoResetPolicy demoResetPolicy,
            CurrentUserService currentUserService,
            TenantRepository tenantRepository,
            @Value("${app.demo.reset-secret}") String resetSecret
    ) {
        this.demoDataSeeder = demoDataSeeder;
        this.demoResetPolicy = demoResetPolicy;
        this.currentUserService = currentUserService;
        this.tenantRepository = tenantRepository;
        this.resetSecret = resetSecret;
    }

    @GetMapping("/status")
    public DemoStatus status() {
        var tenant = tenantRepository.findById(currentUserService.tenantId()).orElseThrow();
        return new DemoStatus(tenant.isDemoMode(), tenant.getName(), Instant.now());
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public DemoStatus reset(@RequestHeader(value = "X-Demo-Reset-Secret", required = false) String suppliedSecret) {
        var user = currentUserService.requireUser();
        if (!user.hasRole(UserRole.SYSTEM_ADMIN)) {
            throw new ForbiddenOperationException("Only System Admin can reset demo data");
        }
        var tenant = tenantRepository.findById(user.tenantId()).orElseThrow();
        demoResetPolicy.assertResettable(tenant);
        if (suppliedSecret != null && !suppliedSecret.isBlank() && !suppliedSecret.equals(resetSecret)) {
            throw new ForbiddenOperationException("Invalid demo reset secret");
        }
        demoDataSeeder.resetDemoTenant();
        return new DemoStatus(true, "Northstar Retail Group", Instant.now());
    }

    public record DemoStatus(boolean demoMode, String tenantName, Instant checkedAt) {
    }
}
