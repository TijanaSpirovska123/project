package com.example.marketing.insights.analytics.strategy;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.util.InsightObjectType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provider-specific analytics knowledge, selected via {@link ProviderAnalyticsStrategyRegistry}.
 * Standard formulas (CTR, CPC, CPM, ROAS, ...) are canonical and live in
 * {@code MetricAggregationService} — they must NEVER be duplicated here. Implementations only
 * describe what a provider supports and contribute optional provider-specific findings.
 */
public interface ProviderAnalyticsStrategy {

    Provider getProvider();

    ProviderAnalyticsCapabilitiesDto getCapabilities();

    Set<CanonicalMetric> getSupportedMetrics();

    Set<BreakdownDimension> getSupportedBreakdowns();

    Set<InsightObjectType> getSupportedObjectTypes();

    /**
     * Whether this provider can supply an exact, already-deduplicated aggregate reach value for
     * the given scope (e.g. a single entity, where reach is inherently exact). Multi-entity
     * scopes should return false unless the provider genuinely exposes a combined figure for
     * that exact set of entities — summing per-entity reach is never safe (Step 8).
     */
    boolean supportsReachAggregation(AnalyticsScope scope);

    boolean supportsMetric(CanonicalMetric metric, InsightObjectType objectType);

    /** Returns a validation error message if this request is unsupported by this provider (e.g. an unsupported breakdown dimension); empty if valid. */
    Optional<String> validateAnalyticsRequest(AnalyticsFilterRequest request);

    /**
     * Optional additional findings only this provider's semantics can justify (e.g. Meta
     * objective-specific rules). Must never duplicate generic mathematical findings already
     * produced by the shared FindingEngine — those never belong here.
     */
    List<DeterministicFindingDto> createProviderSpecificFindings(AnalysisContextDto context);

    /**
     * Reads this provider's account-level currency code off an already-synced snapshot, if this
     * provider exposes one. The ONLY place a provider strategy is allowed to inspect anything
     * beyond canonical normalized metrics (rawData) — used solely for the currency-safety checks
     * in Step 19, never for metric computation.
     */
    default Optional<String> extractCurrency(InsightSnapshotDto snapshot) {
        return Optional.empty();
    }
}
