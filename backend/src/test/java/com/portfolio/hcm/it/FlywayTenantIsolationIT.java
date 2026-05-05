package com.portfolio.hcm.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FlywayTenantIsolationIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workforce_hcm_it")
            .withUsername("workforce")
            .withPassword("workforce");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.demo.seed", () -> "false");
        registry.add("app.jwt.secret", () -> "it-secret-key-that-is-long-enough-for-hmac");
        registry.add("app.demo.reset-secret", () -> "it-reset-secret");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAuditIndexesAndTenantScopedAuditRowsStaySeparate() {
        var tenantA = UUID.randomUUID();
        var tenantB = UUID.randomUUID();
        insertAuditLog(tenantA, "employee.updated");
        insertAuditLog(tenantB, "payroll.preview.generated");

        var tenantACount = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where tenant_id = ? and deleted = false",
                Long.class,
                tenantA
        );
        var indexCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = 'idx_audit_logs_tenant_created'",
                Long.class
        );

        assertThat(tenantACount).isEqualTo(1);
        assertThat(indexCount).isEqualTo(1);
    }

    private void insertAuditLog(UUID tenantId, String actionType) {
        jdbcTemplate.update(
                """
                        insert into audit_logs
                            (id, tenant_id, actor_email, action_type, entity_type, created_at, updated_at, deleted)
                        values
                            (?, ?, ?, ?, ?, now(), now(), false)
                        """,
                UUID.randomUUID(),
                tenantId,
                "system@it.local",
                actionType,
                "IntegrationSmoke"
        );
    }
}
