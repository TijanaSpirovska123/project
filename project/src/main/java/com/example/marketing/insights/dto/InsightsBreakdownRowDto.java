package com.example.marketing.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsBreakdownRowDto {
    private String dimension;
    private String dimensionValue;
    private double spend;
    private long impressions;
    private long clicks;
    private long reach;
    private double ctr;
    private double roas;
    private double share;
}
