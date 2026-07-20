package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.FindingSeverity;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A structured data-quality limitation (e.g. "ROAS unavailable") — deliberately kept separate
 * from {@link DeterministicFindingDto}, which reports performance observations. A data-quality
 * issue never becomes a performance judgment ("ROAS unavailable" must never be phrased as
 * "campaign has bad ROAS").
 */
@Value
@Builder
public class DataQualityIssueDto {
    String code;
    FindingSeverity severity;
    ScopeRefDto scope;
    String message;
    List<CanonicalMetric> affectedMetrics;
}
