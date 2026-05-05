package com.portfolio.hcm.leave;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveAccrualRunResult;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveBalanceDto;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveCalendarEntryDto;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveDecisionRequest;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestCreate;
import static com.portfolio.hcm.leave.LeaveDtos.LeaveRequestDto;

@RestController
@RequestMapping("/api/v1/leave")
public class LeaveController {
    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/requests")
    public List<LeaveRequestDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine
    ) {
        return leaveService.list(from, to, mine);
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public LeaveRequestDto create(@RequestBody LeaveRequestCreate request) {
        return leaveService.create(request);
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public LeaveRequestDto approve(@PathVariable UUID id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveService.approve(id, request);
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public LeaveRequestDto reject(@PathVariable UUID id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveService.reject(id, request);
    }

    @GetMapping("/balances")
    public List<LeaveBalanceDto> balances(@RequestParam(required = false) UUID employeeId) {
        return leaveService.balances(employeeId);
    }

    @GetMapping("/calendar")
    public List<LeaveCalendarEntryDto> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return leaveService.calendar(from, to);
    }

    @PostMapping("/accruals/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LeaveAccrualRunResult runAccruals(@RequestBody(required = false) LeaveAccrualRunRequest request) {
        return leaveService.runAccruals(request);
    }
}
