package com.portfolio.hcm.schedule;

import com.portfolio.hcm.security.CurrentUserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.schedule.ScheduleDtos.ScheduleAlertDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ScheduleWeekDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftDto;
import static com.portfolio.hcm.schedule.ScheduleDtos.ShiftRequest;
import static com.portfolio.hcm.schedule.ScheduleDtos.WeeklyScheduleDto;

@RestController
@RequestMapping("/api/v1")
public class ScheduleController {
    private final CurrentUserService currentUserService;
    private final ScheduleAlertRepository scheduleAlertRepository;
    private final ScheduleService scheduleService;

    public ScheduleController(
            CurrentUserService currentUserService,
            ScheduleAlertRepository scheduleAlertRepository,
            ScheduleService scheduleService
    ) {
        this.currentUserService = currentUserService;
        this.scheduleAlertRepository = scheduleAlertRepository;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/schedules/alerts")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public List<ScheduleAlertDto> alerts() {
        return scheduleAlertRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(currentUserService.tenantId()).stream()
                .map(ScheduleAlertDto::from)
                .toList();
    }

    @GetMapping("/schedules/weeks")
    public List<ScheduleWeekDto> weeks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return scheduleService.listWeeks(from, to);
    }

    @GetMapping("/schedules/weeks/{weekStartDate}")
    public WeeklyScheduleDto week(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return scheduleService.getWeek(weekStartDate);
    }

    @PostMapping("/schedules/weeks/{weekStartDate}/shifts")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public WeeklyScheduleDto createShift(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @Valid @RequestBody ShiftRequest request
    ) {
        return scheduleService.createShift(weekStartDate, request);
    }

    @PutMapping("/schedules/weeks/{weekStartDate}/shifts/{shiftId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public WeeklyScheduleDto updateShift(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRequest request
    ) {
        return scheduleService.updateShift(weekStartDate, shiftId, request);
    }

    @DeleteMapping("/schedules/weeks/{weekStartDate}/shifts/{shiftId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public WeeklyScheduleDto deleteShift(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @PathVariable UUID shiftId
    ) {
        return scheduleService.deleteShift(weekStartDate, shiftId);
    }

    @PostMapping("/schedules/weeks/{weekStartDate}/validate")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public WeeklyScheduleDto validate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return scheduleService.validateWeek(weekStartDate);
    }

    @PostMapping("/schedules/weeks/{weekStartDate}/publish")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public WeeklyScheduleDto publish(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return scheduleService.publishWeek(weekStartDate);
    }

    @GetMapping("/shifts")
    public List<ShiftDto> shifts() {
        return scheduleService.allShifts();
    }
}
