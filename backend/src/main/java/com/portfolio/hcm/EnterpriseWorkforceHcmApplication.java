package com.portfolio.hcm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class EnterpriseWorkforceHcmApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterpriseWorkforceHcmApplication.class, args);
    }
}
