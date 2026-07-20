package com.example.marketing.insights.analytics.enums;

/** How a metric may be combined across multiple entities/rows. */
public enum MetricCategory {
    /** Safe to sum across rows/entities (spend, impressions, clicks, purchases, ...). */
    ADDITIVE,
    /** Must be recalculated from aggregated numerator/denominator — never summed or averaged directly. */
    DERIVED_RATIO,
    /** Not safely additive across entities (reach, unique clicks) — the same person may be counted more than once. */
    NON_ADDITIVE,
    /** A monetary value — additive only within a single currency. */
    CURRENCY,
    /** A conversion-tracking count. */
    CONVERSION,
    /** A monetary conversion value (e.g. purchaseValue). */
    REVENUE
}
