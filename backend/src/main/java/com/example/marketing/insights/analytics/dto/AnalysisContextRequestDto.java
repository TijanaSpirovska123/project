package com.example.marketing.insights.analytics.dto;

import lombok.Data;

import java.util.List;

/**
 * Request body for POST /api/insights/analytics/context. {@code filter} carries provider/
 * adAccountId/objectType/campaignIds.../dateStart/dateStop (Step 6's shared shape); every other
 * field is an explicit, independently-toggleable section — a section left disabled/absent is
 * simply not computed and comes back {@code null} in {@link AnalysisContextDto} (never an empty
 * list standing in for "not requested"). Never accepts a userId — the authenticated principal is
 * always the source of truth (see InsightsAnalyticsController). Never triggers any external AI/
 * FastAPI/MCP call — returns the context only.
 */
@Data
public class AnalysisContextRequestDto {
    AnalyticsFilterRequest filter;

    ComparisonRequestDto comparison;
    TimeSeriesRequestDto timeSeries;
    RankingRequestDto rankings;
    List<BreakdownRequestDto> breakdowns;

    boolean includeFindings = true;
    boolean includeDataQuality = true;
}
