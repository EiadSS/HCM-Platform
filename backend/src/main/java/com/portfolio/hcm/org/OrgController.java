package com.portfolio.hcm.org;

import com.portfolio.hcm.security.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.portfolio.hcm.org.OrgDtos.DepartmentDto;
import static com.portfolio.hcm.org.OrgDtos.JobTitleDto;
import static com.portfolio.hcm.org.OrgDtos.LocationDto;
import static com.portfolio.hcm.org.OrgDtos.OrganizationResponse;

@RestController
@RequestMapping("/api/v1")
public class OrgController {
    private final CurrentUserService currentUserService;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final JobTitleRepository jobTitleRepository;

    public OrgController(
            CurrentUserService currentUserService,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            JobTitleRepository jobTitleRepository
    ) {
        this.currentUserService = currentUserService;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.jobTitleRepository = jobTitleRepository;
    }

    @GetMapping("/organization")
    public OrganizationResponse organization() {
        var tenantId = currentUserService.tenantId();
        return new OrganizationResponse(
                departmentRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream().map(DepartmentDto::from).toList(),
                locationRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream().map(LocationDto::from).toList(),
                jobTitleRepository.findByTenantIdAndDeletedFalseOrderByName(tenantId).stream().map(JobTitleDto::from).toList()
        );
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return departmentRepository.findByTenantIdAndDeletedFalseOrderByName(currentUserService.tenantId()).stream()
                .map(DepartmentDto::from)
                .toList();
    }

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return locationRepository.findByTenantIdAndDeletedFalseOrderByName(currentUserService.tenantId()).stream()
                .map(LocationDto::from)
                .toList();
    }

    @GetMapping("/job-titles")
    public List<JobTitleDto> jobTitles() {
        return jobTitleRepository.findByTenantIdAndDeletedFalseOrderByName(currentUserService.tenantId()).stream()
                .map(JobTitleDto::from)
                .toList();
    }
}
