package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.ComparisonMode;
import lombok.Data;

import java.time.LocalDate;

/** Request body for POST /api/insights/analytics/context (Step 17). Never triggers any external AI/FastAPI/MCP call — returns the context only. */
@Data
public class AnalysisContextRequestDto {
    AnalyticsFilterRequest filter;
    ComparisonMode comparisonMode;
    LocalDate customComparisonStart;
    LocalDate customComparisonStop;
}
