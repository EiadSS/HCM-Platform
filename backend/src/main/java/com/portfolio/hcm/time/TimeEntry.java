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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "time_entries")
public class TimeEntry extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID timesheetId;

    @Column(nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String employeeName;

    private UUID shiftId;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private Instant clockInAt;

    private Instant clockOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeEntrySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeEntryStatus status;

    @Column(columnDefinition = "text")
    private String note;
}
