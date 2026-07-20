package com.example.marketing.insights.analytics.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CoverageDto {
    boolean syncComplete;
    Integer daysWithActivity;
}
