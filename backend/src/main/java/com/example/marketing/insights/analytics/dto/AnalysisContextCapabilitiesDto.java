package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class AnalysisContextCapabilitiesDto {
    Set<CanonicalMetric> supportedMetrics;
    Set<BreakdownDimension> supportedBreakdowns;
    boolean conversionMetricsAvailable;
    boolean supportsDailyTimeSeries;
    boolean supportsComparison;
    boolean supportsExactAggregateReach;
}
