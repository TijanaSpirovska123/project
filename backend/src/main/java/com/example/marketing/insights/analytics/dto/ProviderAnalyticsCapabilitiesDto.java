package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * Provider-independent capability description — lets the frontend and any future Smart Goal
 * integration discover what a provider supports without hard-coding Meta assumptions.
 */
@Value
@Builder
public class ProviderAnalyticsCapabilitiesDto {
    Provider provider;
    Set<InsightObjectType> supportedObjectTypes;
    Set<BreakdownDimension> supportedBreakdowns;
    Set<CanonicalMetric> supportedMetrics;
    boolean conversionMetricsAvailable;
    boolean supportsDailyTimeSeries;
    boolean supportsComparison;
    boolean supportsExactReachAggregation;
}
