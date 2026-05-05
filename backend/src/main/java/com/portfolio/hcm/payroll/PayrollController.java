package com.portfolio.hcm.payroll;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDetailDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewDto;
import static com.portfolio.hcm.payroll.PayrollDtos.PayrollPreviewRequest;

@RestController
@RequestMapping("/api/v1/payroll/previews")
public class PayrollController {
    private final PayrollPreviewService payrollPreviewService;

    public PayrollController(PayrollPreviewService payrollPreviewService) {
        this.payrollPreviewService = payrollPreviewService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public List<PayrollPreviewDto> list() {
        return payrollPreviewService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public PayrollPreviewDetailDto detail(@PathVariable UUID id) {
        return payrollPreviewService.detail(id);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('PAYROLL_ADMIN','SYSTEM_ADMIN')")
    public PayrollPreviewDetailDto generate(@RequestBody(required = false) PayrollPreviewRequest request) {
        return payrollPreviewService.generate(request);
    }
}
