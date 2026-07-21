package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightPeriodDto;
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
    CoverageDto coverage;

    AnalyticsSummaryDto summary;
    /** Null when comparison was not requested/enabled — never an empty placeholder. */
    PeriodComparisonResultDto comparison;
    /** Null when the time-series section was not requested/enabled. */
    TimeSeriesResponseDto timeSeries;
    /** Null when the rankings section was not requested/enabled. */
    AnalyticsRankingsDto rankings;
    /** Null when no breakdown dimensions were requested. */
    List<AnalyticsBreakdownDto> breakdowns;
    /** Null when includeFindings=false. */
    List<DeterministicFindingDto> findings;
    /** Null when includeDataQuality=false. */
    List<DataQualityIssueDto> dataQualityIssues;

    AnalysisContextCapabilitiesDto capabilities;
}
