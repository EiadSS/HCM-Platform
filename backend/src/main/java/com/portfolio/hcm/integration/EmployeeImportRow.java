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

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employee_import_rows")
public class EmployeeImportRow extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID importJobId;

    @Column(nullable = false)
    private int rowNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String rawJson;

    @Column(nullable = false, columnDefinition = "text")
    private String mappedJson;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "text")
    private String errorJson;

    private UUID importedEmployeeId;
}
