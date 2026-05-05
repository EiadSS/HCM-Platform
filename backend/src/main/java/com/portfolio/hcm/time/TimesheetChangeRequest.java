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

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "timesheet_change_requests")
public class TimesheetChangeRequest extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID timesheetId;

    @Column(nullable = false)
    private UUID requestedByUserId;

    @Column(nullable = false)
    private String requesterEmail;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetChangeRequestStatus status;

    @Column(columnDefinition = "text")
    private String decisionNote;

    private UUID decidedByUserId;

    private Instant decidedAt;
}
