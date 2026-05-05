package com.portfolio.hcm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Enterprise Workforce HCM API")
                .version("0.1.0")
                .description("Recruiter-friendly workforce management demo API with RBAC, tenant isolation, audits, payroll previews, and imports.")
                .license(new License().name("Portfolio Demo")));
    }
}
