package com.example.marketing.insights.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;

@Value
@Builder
public class TimeSeriesPointDto {
    LocalDate date;
    /** Keyed by CanonicalMetric.publicName(). Values are BigDecimal-backed MetricValueDto entries — null values preserved (see class-level rules in TimeSeriesService). */
    Map<String, MetricValueDto> metrics;
}
