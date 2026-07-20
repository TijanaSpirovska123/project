package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class RankingEntryDto {
    int rank;
    InsightObjectType objectType;
    String objectExternalId;
    String objectName;
    BigDecimal value;
    String currency;
    InsightPeriodDto activityPeriod;
    /** Sample-size context, so a "top" result based on a tiny volume is visible, not hidden. */
    BigDecimal spend;
    Long impressions;
    Long clicks;
}
