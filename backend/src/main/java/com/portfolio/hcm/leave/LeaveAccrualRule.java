package com.portfolio.hcm.leave;

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
@Table(name = "leave_accrual_rules")
public class LeaveAccrualRule extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String employmentType;

    @Column(nullable = false)
    private String leaveType;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal monthlyAccrualHours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal maxBalanceHours;

    @Column(nullable = false)
    private boolean active;
}
