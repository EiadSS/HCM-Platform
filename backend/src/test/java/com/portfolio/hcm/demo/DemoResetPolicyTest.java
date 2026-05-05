package com.portfolio.hcm.demo;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.tenant.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoResetPolicyTest {
    private final DemoResetPolicy policy = new DemoResetPolicy();

    @Test
    void allowsOnlyDemoTenantsToBeReset() {
        var demoTenant = Tenant.builder().name("Northstar").slug("northstar").status("ACTIVE").demoMode(true).build();
        var realTenant = Tenant.builder().name("Customer").slug("customer").status("ACTIVE").demoMode(false).build();

        assertThatCode(() -> policy.assertResettable(demoTenant)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertResettable(realTenant)).isInstanceOf(ForbiddenOperationException.class);
    }
}
