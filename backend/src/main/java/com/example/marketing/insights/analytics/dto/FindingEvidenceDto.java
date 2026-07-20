package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class FindingEvidenceDto {
    CanonicalMetric metric;
    BigDecimal currentValue;
    BigDecimal previousValue;
    BigDecimal percentageChange;
}
