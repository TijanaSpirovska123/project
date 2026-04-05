package com.example.marketing.insights.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InsightMetricDto {
    private String name;
    private BigDecimal valueNumber;
    private String valueText;
}