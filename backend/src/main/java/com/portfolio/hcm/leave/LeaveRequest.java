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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "leave_requests")
public class LeaveRequest extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String employeeName;

    @Column(nullable = false)
    private String leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false)
    private String status;

    private UUID requestedByUserId;
    private Instant submittedAt;
    private UUID decidedByUserId;
    private Instant decidedAt;

    @Column(columnDefinition = "text")
    private String decisionNote;

    @Column(columnDefinition = "text")
    private String employeeNote;

    @Column(nullable = false)
    private int conflictCount;

    @Column(columnDefinition = "text")
    private String conflictSummary;

    @Column(columnDefinition = "text")
    private String managerNote;
}
