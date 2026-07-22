package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.util.InsightSyncStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class AnalyticsSummaryDto {
    Provider provider;
    String adAccountId;
    InsightPeriodDto requestedPeriod;
    AnalyticsScope scope;
    String currency;
    /** Keyed by CanonicalMetric.publicName() (e.g. "spend", "ctr", "purchaseValue"). */
    Map<String, MetricValueDto> metrics;
    InsightSyncStatus syncStatus;
    List<InsightWarningDto> warnings;
}
