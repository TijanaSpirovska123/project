package com.example.marketing.insights.analytics.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CoverageDto {
    boolean syncComplete;
    Integer daysWithActivity;

    /** Shared by PeriodComparisonService and AnalysisContextBuilder so "days with activity" is always computed the same way from a dataset's own records. */
    public static CoverageDto of(CanonicalDataset dataset) {
        long distinctDaysWithActivity = dataset.getRecords().stream()
                .map(CanonicalInsightRecord::getDate)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        return CoverageDto.builder()
                .syncComplete(dataset.isOverallSyncComplete())
                .daysWithActivity((int) distinctDaysWithActivity)
                .build();
    }
}
