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

    /** null when its denominator (impressions/spend/totalSpend) is zero or absent — undefined, not a fabricated 0. */
    private Double ctr;

    /**
     * Null when purchase/conversion tracking data was not present for this dimension bucket at
     * all (not merely when spend is zero) — a fabricated 0 here would be indistinguishable from
     * a real, tracked zero-value outcome. See conversionDataAvailable.
     */
    private Double roas;

    /** Whether purchase/conversion tracking data was present at all for this bucket — false means roas is unavailable (null), not necessarily zero. */
    private Boolean conversionDataAvailable;

    /** Share of the total, expressed as this dimension's proportion of SPEND (see shareMetric) — not impressions or clicks. */
    private Double share;

    /** What "share" is a percentage of. Currently always "SPEND". */
    @lombok.Builder.Default
    private String shareMetric = "SPEND";
}
