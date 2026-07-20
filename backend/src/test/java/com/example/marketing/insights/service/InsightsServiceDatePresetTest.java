package com.example.marketing.insights.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies resolveDatesForPreset's date math directly. Reproduces (and confirms the fix for)
 * the bug where "last_month" resolved to a rolling 30-day window (today.minusDays(30) to
 * today) — identical to "last_30d" — instead of the actual previous calendar month, which is
 * what "last_month" means both semantically and in Meta's own preset naming (last_30d exists
 * as a separate, distinct preset precisely because it means something different).
 */
class InsightsServiceDatePresetTest {

    private final InsightsService service = new InsightsService(null, null, null, null, null, null, null);

    @Test
    void lastMonth_regularMonth_resolvesToPreviousCalendarMonth() {
        LocalDate today = LocalDate.of(2026, 4, 15);

        LocalDate[] range = service.resolveDatesForPreset("last_month", today);

        assertThat(range[0]).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(range[1]).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void lastMonth_yearBoundary_resolvesToDecemberOfPreviousYear() {
        LocalDate today = LocalDate.of(2026, 1, 10);

        LocalDate[] range = service.resolveDatesForPreset("last_month", today);

        assertThat(range[0]).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(range[1]).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void lastMonth_leapYearFebruary_resolvesToTwentyNineDays() {
        // 2028 is a leap year — last_month as of March 2028 must be Feb 1–29, not Feb 1–28.
        LocalDate today = LocalDate.of(2028, 3, 5);

        LocalDate[] range = service.resolveDatesForPreset("last_month", today);

        assertThat(range[0]).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(range[1]).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void last30d_isStillARollingWindow_distinctFromLastMonth() {
        LocalDate today = LocalDate.of(2026, 4, 15);

        LocalDate[] range = service.resolveDatesForPreset("last_30d", today);

        assertThat(range[0]).isEqualTo(today.minusDays(30));
        assertThat(range[1]).isEqualTo(today);
    }

    @Test
    void today_and_yesterday_resolveToSingleDayRanges() {
        LocalDate today = LocalDate.of(2026, 4, 15);

        assertThat(service.resolveDatesForPreset("today", today))
                .containsExactly(today, today);
        assertThat(service.resolveDatesForPreset("yesterday", today))
                .containsExactly(today.minusDays(1), today.minusDays(1));
    }

    @Test
    void thisMonth_and_thisYear_useCalendarBoundaries() {
        LocalDate today = LocalDate.of(2026, 4, 15);

        assertThat(service.resolveDatesForPreset("this_month", today))
                .containsExactly(LocalDate.of(2026, 4, 1), today);
        assertThat(service.resolveDatesForPreset("this_year", today))
                .containsExactly(LocalDate.of(2026, 1, 1), today);
    }
}
