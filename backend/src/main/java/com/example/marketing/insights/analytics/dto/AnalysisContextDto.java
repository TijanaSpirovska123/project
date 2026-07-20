package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Stable, provider-independent contract intended to be consumable by the Smart Goal service in
 * a LATER phase. Contains no raw provider JSON, no access tokens/credentials, and no personally
 * identifiable advertising-user data — only canonical, nullable-aware metrics and deterministic
 * findings. Building this context never calls any external AI/FastAPI/MCP service; see
 * AnalysisContextBuilder.
 */
@Value
@Builder
public class AnalysisContextDto {
    String schemaVersion;
    Provider provider;
    String adAccountId;
    String currency;
    String timezone;
    Instant generatedAt;

    AnalyticsScope scope;
    InsightPeriodDto currentPeriod;
    InsightPeriodDto comparisonPeriod;

    AnalyticsSummaryDto summary;
    PeriodComparisonResultDto comparison;
    List<TimeSeriesPointDto> timeSeries;
    List<RankingEntryDto> rankings;
    List<InsightsBreakdownRowDto> breakdowns;
    List<DeterministicFindingDto> findings;
    List<DataQualityIssueDto> dataQualityIssues;

    AnalysisContextCapabilitiesDto capabilities;
}
