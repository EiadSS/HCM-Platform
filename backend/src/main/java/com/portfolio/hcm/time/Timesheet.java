package com.portfolio.hcm.time;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "timesheets")
public class Timesheet extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String employeeName;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal regularHours;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal overtimeHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status;

    private Instant submittedAt;
    private Instant approvedAt;
    private UUID approverUserId;

    @Column(columnDefinition = "text")
    private String managerNote;

    @Column(nullable = false)
    private boolean lockedPayPeriod;
}
