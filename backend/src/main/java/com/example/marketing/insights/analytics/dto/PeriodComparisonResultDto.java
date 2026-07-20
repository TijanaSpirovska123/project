package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PeriodComparisonResultDto {
    InsightPeriodDto currentPeriod;
    InsightPeriodDto comparisonPeriod;
    List<ComparisonMetricDto> metrics;
    CoverageDto currentCoverage;
    CoverageDto comparisonCoverage;
}
