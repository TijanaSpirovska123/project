package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import lombok.Data;

/** One requested breakdown dimension inside {@link AnalysisContextRequestDto#getBreakdowns()}. */
@Data
public class BreakdownRequestDto {
    BreakdownDimension dimension;
    /** Informational only today — every breakdown's share is computed against SPEND (see BreakdownAnalyticsService); carried through so the response is self-describing. */
    CanonicalMetric shareMetric = CanonicalMetric.SPEND;
}
