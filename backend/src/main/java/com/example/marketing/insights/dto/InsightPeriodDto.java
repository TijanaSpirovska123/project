package com.example.marketing.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A date range. {@code stop} is always inclusive throughout this API — matching Meta's own
 * time_range.until semantics — so a period of start=2026-06-16, stop=2026-07-16 covers data
 * through and including July 16th.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightPeriodDto {
    private LocalDate start;
    private LocalDate stop;
}
