package com.portfolio.hcm.demo;

import com.portfolio.hcm.common.ForbiddenOperationException;
import com.portfolio.hcm.tenant.Tenant;
import org.springframework.stereotype.Component;

@Component
public class DemoResetPolicy {
    public void assertResettable(Tenant tenant) {
        if (tenant == null || !tenant.isDemoMode()) {
            throw new ForbiddenOperationException("Only demo tenants can be reset");
        }
    }
}
