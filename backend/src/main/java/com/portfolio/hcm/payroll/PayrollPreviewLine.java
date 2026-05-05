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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payroll_preview_lines")
public class PayrollPreviewLine extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID payrollPreviewId;

    @Column(nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String employeeName;

    private UUID locationId;
    private String locationName;

    @Column(nullable = false)
    private int timesheetCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal regularHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal overtimeHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal holidayHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal unpaidBreakHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal unpaidLeaveHours;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPay;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimePay;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal holidayPremiumPay;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;
}
