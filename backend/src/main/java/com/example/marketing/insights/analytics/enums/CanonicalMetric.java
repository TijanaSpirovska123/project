package com.example.marketing.insights.analytics.enums;

/**
 * Provider-independent metric vocabulary for the analytics layer. Never a provider-specific
 * string such as "actions.landing_page_view" or "outbound_clicks.outbound_click" — those stay
 * confined to providerMetrics (see InsightSnapshotDto) and are never read by shared analytics
 * services. Each constant maps to exactly one normalized metric name already produced by
 * InsightMetricsExtractor (Phase 1) via {@link #normalizedName()}.
 */
public enum CanonicalMetric {
    SPEND("spend", MetricCategory.CURRENCY),
    IMPRESSIONS("impressions", MetricCategory.ADDITIVE),
    REACH("reach", MetricCategory.NON_ADDITIVE),
    CLICKS("clicks", MetricCategory.ADDITIVE),
    UNIQUE_CLICKS("unique_clicks", MetricCategory.NON_ADDITIVE),
    OUTBOUND_CLICKS("outbound_clicks.outbound_click", MetricCategory.NON_ADDITIVE),
    LANDING_PAGE_VIEWS("landingPageViews", MetricCategory.ADDITIVE),
    LEADS("leads", MetricCategory.CONVERSION),
    CONVERSIONS("conversions", MetricCategory.CONVERSION),
    PURCHASES("purchases", MetricCategory.CONVERSION),
    PURCHASE_VALUE("purchaseValue", MetricCategory.REVENUE),
    CTR("ctr", MetricCategory.DERIVED_RATIO),
    CPC("cpc", MetricCategory.DERIVED_RATIO),
    CPM("cpm", MetricCategory.DERIVED_RATIO),
    FREQUENCY("frequency", MetricCategory.DERIVED_RATIO),
    CONVERSION_RATE("conversionRate", MetricCategory.DERIVED_RATIO),
    COST_PER_LEAD("costPerLead", MetricCategory.DERIVED_RATIO),
    COST_PER_CONVERSION("costPerConversion", MetricCategory.DERIVED_RATIO),
    COST_PER_PURCHASE("costPerPurchase", MetricCategory.DERIVED_RATIO),
    ROAS("roas", MetricCategory.DERIVED_RATIO);

    private final String normalizedName;
    private final MetricCategory category;

    CanonicalMetric(String normalizedName, MetricCategory category) {
        this.normalizedName = normalizedName;
        this.category = category;
    }

    /** The Phase-1 normalizedMetrics/InsightMetricDto.name this canonical metric reads from. */
    public String normalizedName() {
        return normalizedName;
    }

    public MetricCategory category() {
        return category;
    }

    public static CanonicalMetric fromNormalizedName(String name) {
        for (CanonicalMetric m : values()) {
            if (m.normalizedName.equals(name)) return m;
        }
        return null;
    }
}
