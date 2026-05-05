package com.portfolio.hcm.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.portfolio.hcm.time.TimesheetValidationService.BreakDraft;
import static com.portfolio.hcm.time.TimesheetValidationService.TimeEntryDraft;
import static org.assertj.core.api.Assertions.assertThat;

class TimesheetValidationServiceTest {
    private final TimesheetValidationService service = new TimesheetValidationService();

    @Test
    void flagsLockedPeriodsMissedPunchesAndBadBreaks() {
        var errors = service.validate(List.of(
                new TimeEntryDraft("Jordan Kim", Instant.parse("2026-05-01T13:00:00Z"), null, 30),
                new TimeEntryDraft("Maya Thompson", Instant.parse("2026-05-01T13:00:00Z"), Instant.parse("2026-05-01T17:00:00Z"), 500)
        ), true);

        assertThat(errors).anyMatch(error -> error.contains("Pay period is locked"));
        assertThat(errors).anyMatch(error -> error.contains("Missed punch"));
        assertThat(errors).anyMatch(error -> error.contains("break duration is invalid"));
    }

    @Test
    void flagsOverlapsBadBreaksAndScheduleWarnings() {
        var issues = service.validateDetailed(List.of(
                new TimeEntryDraft(null, "Jordan Kim", LocalDate.of(2026, 5, 4), Instant.parse("2026-05-04T13:10:00Z"), Instant.parse("2026-05-04T21:00:00Z"), 30, List.of(
                        new BreakDraft(Instant.parse("2026-05-04T16:00:00Z"), Instant.parse("2026-05-04T16:30:00Z")),
                        new BreakDraft(Instant.parse("2026-05-04T16:20:00Z"), Instant.parse("2026-05-04T16:45:00Z"))
                ), Instant.parse("2026-05-04T13:00:00Z"), Instant.parse("2026-05-04T21:30:00Z"), false),
                new TimeEntryDraft(null, "Jordan Kim", LocalDate.of(2026, 5, 4), Instant.parse("2026-05-04T20:30:00Z"), Instant.parse("2026-05-04T22:00:00Z"), 0, List.of(), null, null, false)
        ), false);

        assertThat(issues).extracting("type")
                .contains("LATE_ARRIVAL", "EARLY_DEPARTURE", "OVERLAPPING_BREAKS", "OVERLAPPING_ENTRIES");
    }
}
