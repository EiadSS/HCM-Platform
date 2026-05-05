package com.portfolio.hcm.demo;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.security.AuthenticatedUser;
import com.portfolio.hcm.security.CurrentUserService;
import com.portfolio.hcm.tenant.Tenant;
import com.portfolio.hcm.tenant.TenantRepository;
import com.portfolio.hcm.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoControllerTest {
    private final DemoDataSeeder seeder = mock(DemoDataSeeder.class);
    private final DemoResetPolicy resetPolicy = new DemoResetPolicy();
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final DemoController controller = new DemoController(seeder, resetPolicy, currentUserService, tenantRepository, "expected-secret");

    @Test
    void resetRejectsNonSystemUsersEvenBeforeMutatingDemoData() {
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "manager@demo.hcm.local", Set.of(UserRole.MANAGER));
        when(currentUserService.requireUser()).thenReturn(user);

        assertThatThrownBy(() -> controller.reset(null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only System Admin");
    }

    @Test
    void resetRejectsInvalidSuppliedSecretForDemoTenant() {
        var tenantId = UUID.randomUUID();
        var user = new AuthenticatedUser(UUID.randomUUID(), tenantId, "admin@demo.hcm.local", Set.of(UserRole.SYSTEM_ADMIN));
        var tenant = Tenant.builder().name("Northstar Retail Group").slug("northstar").status("ACTIVE").demoMode(true).build();
        tenant.setId(tenantId);
        when(currentUserService.requireUser()).thenReturn(user);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> controller.reset("wrong-secret"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Invalid demo reset secret");
    }
}
