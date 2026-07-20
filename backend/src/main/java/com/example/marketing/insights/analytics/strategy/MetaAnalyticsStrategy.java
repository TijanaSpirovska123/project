package com.example.marketing.insights.analytics.strategy;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Meta-specific analytics knowledge only — no standard formulas (those are canonical, in
 * MetricAggregationService). Meta's own quirks live here: which breakdown dimensions it
 * supports, that it cannot supply an exact combined reach figure across arbitrary entity sets,
 * and where to read its account currency from a raw response.
 */
@Component
public class MetaAnalyticsStrategy implements ProviderAnalyticsStrategy {

    private static final Set<InsightObjectType> SUPPORTED_OBJECT_TYPES =
            EnumSet.of(InsightObjectType.ACCOUNT, InsightObjectType.CAMPAIGN, InsightObjectType.ADSET, InsightObjectType.AD);

    private static final Set<BreakdownDimension> SUPPORTED_BREAKDOWNS =
            EnumSet.of(BreakdownDimension.AGE, BreakdownDimension.GENDER, BreakdownDimension.COUNTRY,
                    BreakdownDimension.PLACEMENT, BreakdownDimension.DEVICE, BreakdownDimension.PLATFORM);

    /** Meta supports every canonical metric this analytics layer defines (conversion metrics may simply be unavailable per-campaign when tracking isn't configured — that's a data-quality state, not an unsupported metric). */
    private static final Set<CanonicalMetric> SUPPORTED_METRICS = EnumSet.allOf(CanonicalMetric.class);

    @Override
    public Provider getProvider() {
        return Provider.META;
    }

    @Override
    public ProviderAnalyticsCapabilitiesDto getCapabilities() {
        return ProviderAnalyticsCapabilitiesDto.builder()
                .provider(Provider.META)
                .supportedObjectTypes(SUPPORTED_OBJECT_TYPES)
                .supportedBreakdowns(SUPPORTED_BREAKDOWNS)
                .supportedMetrics(SUPPORTED_METRICS)
                .conversionMetricsAvailable(true)
                .supportsDailyTimeSeries(true)
                .supportsComparison(true)
                .supportsExactReachAggregation(false)
                .build();
    }

    @Override
    public Set<CanonicalMetric> getSupportedMetrics() {
        return SUPPORTED_METRICS;
    }

    @Override
    public Set<BreakdownDimension> getSupportedBreakdowns() {
        return SUPPORTED_BREAKDOWNS;
    }

    @Override
    public Set<InsightObjectType> getSupportedObjectTypes() {
        return SUPPORTED_OBJECT_TYPES;
    }

    /**
     * Meta's Insights API does not expose a combined, deduplicated reach figure across an
     * arbitrary set of campaigns/ad sets/ads — only per-entity reach (exact for that one entity)
     * or a whole-account reach (exact only when the scope IS the whole account). Neither case
     * applies to an arbitrary multi-entity selection, so this is always false beyond a single
     * entity.
     */
    @Override
    public boolean supportsReachAggregation(AnalyticsScope scope) {
        return scope != null && scope.isSingleEntity();
    }

    @Override
    public boolean supportsMetric(CanonicalMetric metric, InsightObjectType objectType) {
        return SUPPORTED_METRICS.contains(metric) && SUPPORTED_OBJECT_TYPES.contains(objectType);
    }

    @Override
    public Optional<String> validateAnalyticsRequest(AnalyticsFilterRequest request) {
        if (request.getBreakdowns() != null) {
            for (String dim : request.getBreakdowns()) {
                BreakdownDimension canonical = BreakdownDimension.fromWireName(dim);
                if (canonical == null || !SUPPORTED_BREAKDOWNS.contains(canonical)) {
                    return Optional.of("Unsupported breakdown dimension for Meta: " + dim);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DeterministicFindingDto> createProviderSpecificFindings(AnalysisContextDto context) {
        // Extension point for Meta-specific deterministic rules (e.g. objective-aware findings,
        // once campaign objective is joined into CanonicalInsightRecord). None implemented yet —
        // intentionally postponed; see the Phase 2 report's "postponed" section.
        return List.of();
    }

    @Override
    public Optional<String> extractCurrency(InsightSnapshotDto snapshot) {
        Object rawData = snapshot.getRawData();
        if (!(rawData instanceof JsonNode root)) return Optional.empty();
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            String currency = data.get(0).path("account_currency").asText(null);
            if (currency != null && !currency.isBlank()) return Optional.of(currency);
        }
        String topLevel = root.path("account_currency").asText(null);
        return (topLevel != null && !topLevel.isBlank()) ? Optional.of(topLevel) : Optional.empty();
    }
}
