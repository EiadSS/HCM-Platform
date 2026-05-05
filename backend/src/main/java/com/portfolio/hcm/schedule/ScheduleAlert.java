package com.portfolio.hcm.schedule;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "schedule_alerts")
public class ScheduleAlert extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    private UUID employeeId;

    @Column(nullable = false)
    private String employeeName;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private String status;
}
