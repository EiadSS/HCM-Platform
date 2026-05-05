package com.portfolio.hcm.payroll;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PayrollPreviewCalculator {
    public PayrollCalculation calculate(PayrollInput input) {
        var paidHours = input.workedHours().subtract(input.unpaidBreakHours()).max(BigDecimal.ZERO);
        var regularHours = paidHours.min(input.weeklyRegularHours()).setScale(2, RoundingMode.HALF_UP);
        var overtimeHours = paidHours.subtract(regularHours).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        var regularPay = money(regularHours.multiply(input.hourlyRate()));
        var overtimePay = money(overtimeHours.multiply(input.hourlyRate()).multiply(input.overtimeMultiplier()));
        var holidayPremiumPay = money(input.holidayHours().multiply(input.hourlyRate()).multiply(input.holidayPremiumMultiplier().subtract(BigDecimal.ONE)));
        var grossPay = money(regularPay.add(overtimePay).add(holidayPremiumPay));

        var explanation = "%s: %s worked hours minus %s unpaid break hours produced %s payable hours. %s regular hours at $%s and %s overtime hours at %sx generated $%s base pay. %s holiday worked hours added $%s premium under %s. %s approved unpaid leave hours were excluded from gross pay."
                .formatted(
                        input.employeeName(),
                        hours(input.workedHours()),
                        hours(input.unpaidBreakHours()),
                        hours(paidHours),
                        hours(regularHours),
                        money(input.hourlyRate()).toPlainString(),
                        hours(overtimeHours),
                        input.overtimeMultiplier().stripTrailingZeros().toPlainString(),
                        money(regularPay.add(overtimePay)).toPlainString(),
                        hours(input.holidayHours()),
                        holidayPremiumPay.toPlainString(),
                        input.ruleName(),
                        hours(input.unpaidLeaveHours())
                );

        return new PayrollCalculation(
                regularHours,
                overtimeHours,
                input.holidayHours().setScale(2, RoundingMode.HALF_UP),
                input.unpaidBreakHours().setScale(2, RoundingMode.HALF_UP),
                input.unpaidLeaveHours().setScale(2, RoundingMode.HALF_UP),
                regularPay,
                overtimePay,
                holidayPremiumPay,
                grossPay,
                explanation
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String hours(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public record PayrollInput(
            String employeeName,
            BigDecimal workedHours,
            BigDecimal unpaidBreakHours,
            BigDecimal unpaidLeaveHours,
            BigDecimal holidayHours,
            BigDecimal hourlyRate,
            BigDecimal weeklyRegularHours,
            BigDecimal overtimeMultiplier,
            BigDecimal holidayPremiumMultiplier,
            String ruleName
    ) {
        public PayrollInput {
            if (holidayPremiumMultiplier == null) {
                holidayPremiumMultiplier = BigDecimal.ONE;
            }
        }
    }

    public record PayrollCalculation(
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal holidayHours,
            BigDecimal unpaidBreakHours,
            BigDecimal unpaidLeaveHours,
            BigDecimal regularPay,
            BigDecimal overtimePay,
            BigDecimal holidayPremiumPay,
            BigDecimal grossPay,
            String explanation
    ) {
    }
}
