package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.util.InsightObjectType;
import lombok.Builder;
import lombok.Value;

/** Identifies which entity a finding/data-quality issue refers to. */
@Value
@Builder
public class ScopeRefDto {
    InsightObjectType objectType;
    String objectExternalId;
    String objectName;
}
