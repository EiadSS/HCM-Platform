package com.portfolio.hcm.time;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.portfolio.hcm.time.TimesheetDtos.BreakRequest;
import static com.portfolio.hcm.time.TimesheetDtos.ClockRequest;
import static com.portfolio.hcm.time.TimesheetDtos.TimeEntryDto;
import static com.portfolio.hcm.time.TimesheetDtos.TimeStatusDto;

@RestController
@RequestMapping("/api/v1/time")
public class TimeController {
    private final TimeTrackingService timeTrackingService;

    public TimeController(TimeTrackingService timeTrackingService) {
        this.timeTrackingService = timeTrackingService;
    }

    @GetMapping("/me/status")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TimeStatusDto status() {
        return timeTrackingService.status();
    }

    @PostMapping("/clock-in")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TimeStatusDto clockIn(@RequestBody(required = false) ClockRequest request) {
        return timeTrackingService.clockIn(request);
    }

    @PostMapping("/clock-out")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TimeStatusDto clockOut(@RequestBody(required = false) ClockRequest request) {
        return timeTrackingService.clockOut(request);
    }

    @PostMapping("/entries/{entryId}/breaks/start")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public TimeEntryDto startBreak(@PathVariable UUID entryId, @RequestBody(required = false) BreakRequest request) {
        return timeTrackingService.startBreak(entryId, request);
    }

    @PostMapping("/breaks/{breakId}/end")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public TimeEntryDto endBreak(@PathVariable UUID breakId, @RequestBody(required = false) BreakRequest request) {
        return timeTrackingService.endBreak(breakId, request);
    }
}
