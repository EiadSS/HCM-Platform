package com.portfolio.hcm.integration;

import com.portfolio.hcm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "import_jobs")
public class ImportJob extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int successRows;

    @Column(nullable = false)
    private int errorRows;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String errorReportCsv;

    @Column(columnDefinition = "text")
    private String fieldMappingJson;

    private Instant queuedAt;
    private Instant startedAt;
    private Instant previewedAt;
    private Instant committedAt;
    private Instant failedAt;

    @Column(nullable = false)
    private int committedRows;

    @Column(columnDefinition = "text")
    private String sourceMetadata;

    private Instant completedAt;
}
