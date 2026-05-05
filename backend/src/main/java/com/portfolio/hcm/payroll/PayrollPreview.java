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
@Table(name = "payroll_previews")
public class PayrollPreview extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    private UUID locationId;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal regularHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal overtimeHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal unpaidBreakHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal unpaidLeaveHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal holidayHours;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal holidayPremiumPay;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay;

    @Column(nullable = false)
    private int employeeCount;

    @Column(nullable = false)
    private int timesheetCount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    private UUID generatedByUserId;

    @Column(columnDefinition = "text")
    private String metadata;
}
