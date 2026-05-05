package com.portfolio.hcm.payroll;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pay_rule_configs")
public class PayRuleConfig extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    private UUID locationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate effectiveStartDate;

    private LocalDate effectiveEndDate;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weeklyRegularHours;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal overtimeMultiplier;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal holidayPremiumMultiplier;

    @Column(nullable = false)
    private boolean unpaidBreaksDeductible;
}
