package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TimeSeriesResponseDto {
    Provider provider;
    TimeGranularity granularity;
    InsightPeriodDto period;
    List<TimeSeriesPointDto> series;
}
