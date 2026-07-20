package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import lombok.Value;

import java.math.BigDecimal;

/**
 * One metric reading for one {@link CanonicalInsightRecord} — value is null whenever
 * available=false. Never a fabricated zero.
 */
@Value
public class MetricSample {
    BigDecimal value;
    boolean available;
    MetricUnavailableReason unavailableReason;

    public static MetricSample of(BigDecimal value) {
        return new MetricSample(value, true, null);
    }

    public static MetricSample unavailable(MetricUnavailableReason reason) {
        return new MetricSample(null, false, reason);
    }
}
