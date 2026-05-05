package com.portfolio.hcm.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.portfolio.hcm.payroll.PayrollPreviewCalculator.PayrollInput;
import static org.assertj.core.api.Assertions.assertThat;

class PayrollPreviewCalculatorTest {
    private final PayrollPreviewCalculator calculator = new PayrollPreviewCalculator();

    @Test
    void calculatesGrossPayAndExplainsOvertimeBreaksHolidayAndLeave() {
        var result = calculator.calculate(new PayrollInput(
                "Amara Singh",
                new BigDecimal("45.00"),
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("9.00"),
                new BigDecimal("25.00"),
                new BigDecimal("40.00"),
                new BigDecimal("1.50"),
                new BigDecimal("1.50"),
                "Downtown Store rule"
        ));

        assertThat(result.regularHours()).isEqualByComparingTo("40.00");
        assertThat(result.overtimeHours()).isEqualByComparingTo("4.00");
        assertThat(result.regularPay()).isEqualByComparingTo("1000.00");
        assertThat(result.overtimePay()).isEqualByComparingTo("150.00");
        assertThat(result.holidayPremiumPay()).isEqualByComparingTo("112.50");
        assertThat(result.grossPay()).isEqualByComparingTo("1262.50");
        assertThat(result.explanation())
                .contains("Amara Singh")
                .contains("45 worked hours minus 1 unpaid break hours")
                .contains("4 overtime hours")
                .contains("9 holiday worked hours")
                .contains("4 approved unpaid leave hours");
    }
}
