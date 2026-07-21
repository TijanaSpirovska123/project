package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.Data;

/** Comparison section of {@link AnalysisContextRequestDto}. When disabled/absent, AnalysisContextDto.comparison is null. */
@Data
public class ComparisonRequestDto {
    boolean enabled;
    ComparisonMode mode;
    /** Only used when mode == CUSTOM. */
    InsightPeriodDto customPeriod;
}
