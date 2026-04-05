package com.example.marketing.userconfig.dto;

import lombok.Data;

import java.util.List;

@Data
public class InsightsConfigDto {
    /** Ordered list of metric IDs, e.g. ["impressions","reach","clicks","spend","ctr","cpm"] */
    private List<String> metricCards;
}
