package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/** One requested dimension's rows within {@link AnalysisContextDto#getBreakdowns()}. */
@Value
@Builder
public class AnalyticsBreakdownDto {
    BreakdownDimension dimension;
    String shareMetric;
    List<InsightsBreakdownRowDto> rows;
}
