package com.example.marketing.insights.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InsightMetricDto {
    private String name;
    private BigDecimal valueNumber;
    private String valueText;

    /** False when this metric could not be determined at all (see unavailableReason). Defaults
     * to true — most metrics in the list are, by construction, ones that WERE determined;
     * unavailable ones are only added explicitly with available=false and a reason. */
    private boolean available = true;

    /** Set only when available=false. One of InsightUnavailableReason's constants. */
    private String unavailableReason;

    public static InsightMetricDto unavailable(String name, String reason) {
        InsightMetricDto dto = new InsightMetricDto();
        dto.setName(name);
        dto.setAvailable(false);
        dto.setUnavailableReason(reason);
        return dto;
    }
}