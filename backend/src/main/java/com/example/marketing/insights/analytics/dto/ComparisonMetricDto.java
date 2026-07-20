package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ComparisonMetricDto {
    CanonicalMetric metric;
    BigDecimal currentValue;
    BigDecimal previousValue;
    BigDecimal absoluteChange;
    BigDecimal percentageChange;
    /** Set when percentageChange is null despite both values being known — e.g. previous=0, current>0. */
    String changeReason;
    /** INCREASED | DECREASED | UNCHANGED | UNKNOWN */
    String direction;
    boolean available;
    MetricUnavailableReason unavailableReason;
}
