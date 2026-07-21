package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import lombok.Data;

import java.util.List;

/** Time-series section of {@link AnalysisContextRequestDto}. When disabled/absent, AnalysisContextDto.timeSeries is null. */
@Data
public class TimeSeriesRequestDto {
    boolean enabled;
    TimeGranularity granularity = TimeGranularity.DAY;
    /** Optional metric allow-list applied to each point's metrics map after TimeSeriesService computes it; null/empty means every canonical metric is included (unchanged behavior). */
    List<CanonicalMetric> metrics;
    boolean includeInactivePeriods = false;
}
