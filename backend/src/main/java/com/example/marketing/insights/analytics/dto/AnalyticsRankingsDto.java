package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/** {@link AnalysisContextDto#getRankings()} — the metric results were ranked by, plus the ranked entries. */
@Value
@Builder
public class AnalyticsRankingsDto {
    CanonicalMetric metric;
    List<RankingEntryDto> results;
}
