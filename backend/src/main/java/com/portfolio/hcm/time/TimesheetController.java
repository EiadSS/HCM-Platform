package com.portfolio.hcm.time;

import com.portfolio.hcm.audit.AuditLogDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.time.TimesheetDtos.ApprovalRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ChangeRequestRequest;
import static com.portfolio.hcm.time.TimesheetDtos.DecisionRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ManualTimeEntryRequest;
import static com.portfolio.hcm.time.TimesheetDtos.TimeEntryDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetChangeRequestDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetDetailDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimesheetDto;

@RestController
@RequestMapping("/api/v1/timesheets")
public class TimesheetController {
    private final TimesheetService timesheetService;

    public TimesheetController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public List<TimesheetDto> list() {
        return timesheetService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDetailDto detail(@PathVariable UUID id) {
        return timesheetService.detail(id);
    }

    @GetMapping("/weeks/{weekStartDate}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDetailDto currentUserWeek(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return timesheetService.currentUserWeek(weekStartDate);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public List<AuditLogDto> history(@PathVariable UUID id) {
        return timesheetService.detail(id).history();
    }

    @PostMapping("/{id}/entries")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDetailDto addEntry(@PathVariable UUID id, @Valid @RequestBody ManualTimeEntryRequest request) {
        return timesheetService.addEntry(id, request);
    }

    @PutMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDetailDto updateEntry(@PathVariable UUID id, @PathVariable UUID entryId, @Valid @RequestBody ManualTimeEntryRequest request) {
        return timesheetService.updateEntry(id, entryId, request);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDetailDto deleteEntry(@PathVariable UUID id, @PathVariable UUID entryId) {
        return timesheetService.deleteEntry(id, entryId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDto submit(@PathVariable UUID id) {
        return timesheetService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDto approve(@PathVariable UUID id, @RequestBody(required = false) ApprovalRequest request) {
        return timesheetService.approve(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDto reject(@PathVariable UUID id, @RequestBody(required = false) ApprovalRequest request) {
        return timesheetService.reject(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/change-requests")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetChangeRequestDto requestChange(@PathVariable UUID id, @Valid @RequestBody ChangeRequestRequest request) {
        return timesheetService.requestChange(id, request);
    }

    @PostMapping("/{id}/change-requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetChangeRequestDto approveChangeRequest(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) DecisionRequest request) {
        return timesheetService.approveChangeRequest(id, requestId, request);
    }

    @PostMapping("/{id}/change-requests/{requestId}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetChangeRequestDto rejectChangeRequest(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) DecisionRequest request) {
        return timesheetService.rejectChangeRequest(id, requestId, request);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDto lock(@PathVariable UUID id) {
        return timesheetService.lock(id);
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public TimesheetDto unlock(@PathVariable UUID id) {
        return timesheetService.unlock(id);
    }
}
