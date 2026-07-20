package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Shared filter/request shape for every Phase-2 analytics endpoint (summary, time-series,
 * comparison, rankings, breakdown, context). Individual endpoints only use the subset of fields
 * relevant to them; validation of required fields happens per-endpoint in the owning service.
 */
@Data
public class AnalyticsFilterRequest {
    Provider provider;
    String adAccountId;
    InsightObjectType objectType;
    List<String> campaignIds;
    List<String> adSetIds;
    List<String> adIds;
    LocalDate dateStart;
    LocalDate dateStop;
    String timezone;
    List<String> metrics;
    List<String> breakdowns;
    TimeGranularity granularity;
    boolean includeInactivePeriods = false;
}
