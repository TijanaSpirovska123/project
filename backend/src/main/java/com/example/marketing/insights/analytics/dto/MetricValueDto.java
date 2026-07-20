package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** One metric's value + availability metadata, as exposed in every analytics response. */
@Value
@Builder
public class MetricValueDto {
    BigDecimal value;
    boolean available;
    MetricUnavailableReason unavailableReason;
    /** "currency" | "count" | "ratio" | "percentage" */
    String unit;
    String currency;

    public static MetricValueDto available(BigDecimal value, String unit) {
        return MetricValueDto.builder().value(value).available(true).unit(unit).build();
    }

    public static MetricValueDto available(BigDecimal value, String unit, String currency) {
        return MetricValueDto.builder().value(value).available(true).unit(unit).currency(currency).build();
    }

    public static MetricValueDto unavailable(MetricUnavailableReason reason, String unit) {
        return MetricValueDto.builder().value(null).available(false).unavailableReason(reason).unit(unit).build();
    }
}
