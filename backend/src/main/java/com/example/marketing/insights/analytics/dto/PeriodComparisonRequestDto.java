package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Request body for POST /api/insights/analytics/compare (Step 11). */
@Data
public class PeriodComparisonRequestDto {
    Provider provider;
    String adAccountId;
    InsightObjectType objectType;
    List<String> campaignIds;
    List<String> adSetIds;
    List<String> adIds;
    LocalDate currentPeriodStart;
    LocalDate currentPeriodStop;
    ComparisonMode comparisonMode;
    /** Only used when comparisonMode == CUSTOM. */
    LocalDate customComparisonStart;
    LocalDate customComparisonStop;
}
