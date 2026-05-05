package com.portfolio.hcm.org;

import java.util.UUID;

public final class OrgDtos {
    private OrgDtos() {
    }

    public record DepartmentDto(UUID id, String name, String costCenter) {
        static DepartmentDto from(Department department) {
            return new DepartmentDto(department.getId(), department.getName(), department.getCostCenter());
        }
    }

    public record LocationDto(UUID id, String name, String timezone, String region) {
        static LocationDto from(Location location) {
            return new LocationDto(location.getId(), location.getName(), location.getTimezone(), location.getRegion());
        }
    }

    public record JobTitleDto(UUID id, String name, String careerLevel) {
        static JobTitleDto from(JobTitle jobTitle) {
            return new JobTitleDto(jobTitle.getId(), jobTitle.getName(), jobTitle.getCareerLevel());
        }
    }

    public record OrganizationResponse(
            java.util.List<DepartmentDto> departments,
            java.util.List<LocationDto> locations,
            java.util.List<JobTitleDto> jobTitles
    ) {
    }
}
