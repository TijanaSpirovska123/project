package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Data;

import java.math.BigDecimal;

/** Rankings section of {@link AnalysisContextRequestDto}. When disabled/absent, AnalysisContextDto.rankings is null. */
@Data
public class RankingRequestDto {
    boolean enabled;
    /** Defaults to the filter's own object type/selection when not set. */
    InsightObjectType objectType;
    CanonicalMetric metric = CanonicalMetric.SPEND;
    String direction = "DESC";
    int limit = 10;
    BigDecimal minimumSpend;
    BigDecimal minimumImpressions;
    boolean includeUnavailable = false;
}
