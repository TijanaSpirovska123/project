package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.dto.MetricValueDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The one place canonical ratio formulas live. Never averages a pre-computed ratio across
 * records — every DERIVED_RATIO metric is recalculated from aggregated numerator/denominator
 * additive sums. Intermediate division uses a wide MathContext; rounding to 2 decimal places
 * happens only at the DTO boundary (round2), never on intermediate sums.
 */
@Service
public class MetricAggregationService {

    /** Wide enough that intermediate ratio division never loses precision before final rounding. */
    public static final MathContext INTERMEDIATE_MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    public static final int DISPLAY_SCALE = 2;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    /** additive/non-additive base metrics this service sums directly (everything except DERIVED_RATIO, which is recomputed). */
    private static final List<CanonicalMetric> ADDITIVE_METRICS = List.of(
            CanonicalMetric.SPEND, CanonicalMetric.IMPRESSIONS, CanonicalMetric.CLICKS,
            CanonicalMetric.UNIQUE_CLICKS, CanonicalMetric.OUTBOUND_CLICKS, CanonicalMetric.LANDING_PAGE_VIEWS,
            CanonicalMetric.LEADS, CanonicalMetric.CONVERSIONS, CanonicalMetric.PURCHASES, CanonicalMetric.PURCHASE_VALUE);

    /** One numerator/denominator ratio formula, recalculated fresh from summed totals every time. */
    private record RatioFormula(CanonicalMetric metric, CanonicalMetric numerator, CanonicalMetric denominator, BigDecimal multiplier) {}

    private static final List<RatioFormula> RATIO_FORMULAS = List.of(
            new RatioFormula(CanonicalMetric.CTR, CanonicalMetric.CLICKS, CanonicalMetric.IMPRESSIONS, ONE_HUNDRED),
            new RatioFormula(CanonicalMetric.CPC, CanonicalMetric.SPEND, CanonicalMetric.CLICKS, null),
            new RatioFormula(CanonicalMetric.CPM, CanonicalMetric.SPEND, CanonicalMetric.IMPRESSIONS, ONE_THOUSAND),
            new RatioFormula(CanonicalMetric.CONVERSION_RATE, CanonicalMetric.CONVERSIONS, CanonicalMetric.CLICKS, ONE_HUNDRED),
            new RatioFormula(CanonicalMetric.COST_PER_LEAD, CanonicalMetric.SPEND, CanonicalMetric.LEADS, null),
            new RatioFormula(CanonicalMetric.COST_PER_CONVERSION, CanonicalMetric.SPEND, CanonicalMetric.CONVERSIONS, null),
            new RatioFormula(CanonicalMetric.COST_PER_PURCHASE, CanonicalMetric.SPEND, CanonicalMetric.PURCHASES, null),
            new RatioFormula(CanonicalMetric.ROAS, CanonicalMetric.PURCHASE_VALUE, CanonicalMetric.SPEND, null));

    /** Monetary metrics that must become unavailable (MIXED_CURRENCY) when records span more than one currency. */
    private static final List<CanonicalMetric> MONETARY_METRICS = List.of(
            CanonicalMetric.SPEND, CanonicalMetric.PURCHASE_VALUE, CanonicalMetric.CPC, CanonicalMetric.CPM,
            CanonicalMetric.COST_PER_LEAD, CanonicalMetric.COST_PER_CONVERSION, CanonicalMetric.COST_PER_PURCHASE,
            CanonicalMetric.ROAS);

    public record AggregatedMetric(BigDecimal rawValue, boolean available, MetricUnavailableReason unavailableReason) {
        static AggregatedMetric unavailable(MetricUnavailableReason reason) {
            return new AggregatedMetric(null, false, reason);
        }
        static AggregatedMetric available(BigDecimal value) {
            return new AggregatedMetric(value, true, null);
        }
    }

    public record AggregationResult(Map<CanonicalMetric, AggregatedMetric> metrics, String currency, boolean mixedCurrency) {
        public MetricValueDto toDto(CanonicalMetric metric, String unit) {
            AggregatedMetric m = metrics.get(metric);
            if (m == null || !m.available()) {
                MetricUnavailableReason reason = m != null ? m.unavailableReason() : MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER;
                return MetricValueDto.unavailable(reason, unit);
            }
            BigDecimal rounded = m.rawValue().setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
            return "currency".equals(unit)
                    ? MetricValueDto.available(rounded, unit, currency)
                    : MetricValueDto.available(rounded, unit);
        }
    }

    /**
     * Aggregates additive base metrics across records and recalculates every derived ratio from
     * the resulting sums. Reach/frequency follow Step 8's non-additive rules via {@code strategy}
     * and {@code scope}. Currency-mixing across records disables every monetary metric.
     */
    public AggregationResult aggregate(List<CanonicalInsightRecord> records, ProviderAnalyticsStrategy strategy, AnalyticsScope scope) {
        Map<CanonicalMetric, AggregatedMetric> result = new EnumMap<>(CanonicalMetric.class);

        java.util.Set<String> currencies = new java.util.LinkedHashSet<>();
        for (CanonicalInsightRecord r : records) {
            if (r.getCurrency() != null) currencies.add(r.getCurrency());
        }
        boolean mixedCurrency = currencies.size() > 1;
        String currency = currencies.size() == 1 ? currencies.iterator().next() : null;

        for (CanonicalMetric metric : ADDITIVE_METRICS) {
            result.put(metric, sumAdditive(records, metric));
        }

        result.put(CanonicalMetric.REACH, aggregateReach(records, strategy, scope));

        for (RatioFormula f : RATIO_FORMULAS) {
            result.put(f.metric(), computeRatio(result, f));
        }
        result.put(CanonicalMetric.FREQUENCY, computeFrequency(result));

        if (mixedCurrency) {
            for (CanonicalMetric monetary : MONETARY_METRICS) {
                result.put(monetary, AggregatedMetric.unavailable(MetricUnavailableReason.MIXED_CURRENCY));
            }
        }

        return new AggregationResult(result, currency, mixedCurrency);
    }

    /**
     * Sums an additive metric across records that report it as available; records where it's
     * unavailable simply don't contribute (never treated as a fabricated zero). Available only
     * if at least one contributing record had a real value — otherwise unavailable, preserving
     * the most common reason among the non-contributing records.
     */
    private AggregatedMetric sumAdditive(List<CanonicalInsightRecord> records, CanonicalMetric metric) {
        BigDecimal sum = null;
        Map<MetricUnavailableReason, Integer> reasonCounts = new EnumMap<>(MetricUnavailableReason.class);

        for (CanonicalInsightRecord r : records) {
            MetricSample s = r.metric(metric);
            if (s.isAvailable() && s.getValue() != null) {
                sum = sum == null ? s.getValue() : sum.add(s.getValue());
            } else if (s.getUnavailableReason() != null) {
                reasonCounts.merge(s.getUnavailableReason(), 1, Integer::sum);
            }
        }

        if (sum != null) return AggregatedMetric.available(sum);

        MetricUnavailableReason mostCommon = reasonCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER);
        return AggregatedMetric.unavailable(mostCommon);
    }

    /**
     * Reach is never blindly summed across entities — the same person may be counted in more
     * than one campaign/ad set/ad. A single-entity scope may use that entity's own reach value
     * directly (it's already exact for that entity); anything wider needs the provider strategy
     * to confirm it can supply an exact aggregate for this scope, which no provider currently
     * does, so multi-entity reach/frequency are NON_ADDITIVE_AGGREGATION by default.
     */
    private AggregatedMetric aggregateReach(List<CanonicalInsightRecord> records, ProviderAnalyticsStrategy strategy, AnalyticsScope scope) {
        if (records.size() == 1 || (scope != null && scope.isSingleEntity())) {
            MetricSample s = records.isEmpty() ? MetricSample.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER)
                    : records.get(0).metric(CanonicalMetric.REACH);
            return s.isAvailable() && s.getValue() != null
                    ? AggregatedMetric.available(s.getValue())
                    : AggregatedMetric.unavailable(s.getUnavailableReason() != null ? s.getUnavailableReason() : MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER);
        }
        if (strategy != null && scope != null && strategy.supportsReachAggregation(scope)) {
            // Provider confirmed it can supply an exact aggregate for this exact scope — sum as additive.
            return sumAdditive(records, CanonicalMetric.REACH);
        }
        return AggregatedMetric.unavailable(MetricUnavailableReason.NON_ADDITIVE_AGGREGATION);
    }

    private AggregatedMetric computeRatio(Map<CanonicalMetric, AggregatedMetric> sums, RatioFormula f) {
        AggregatedMetric num = sums.get(f.numerator());
        AggregatedMetric denom = sums.get(f.denominator());
        if (num == null || denom == null || !num.available() || !denom.available()) {
            MetricUnavailableReason reason = pickUnavailableReason(num, denom);
            return AggregatedMetric.unavailable(reason);
        }
        return divide(num.rawValue(), denom.rawValue(), f.multiplier());
    }

    private AggregatedMetric computeFrequency(Map<CanonicalMetric, AggregatedMetric> sums) {
        AggregatedMetric impressions = sums.get(CanonicalMetric.IMPRESSIONS);
        AggregatedMetric reach = sums.get(CanonicalMetric.REACH);
        // Frequency must be null whenever reliable aggregate reach is unavailable — even if
        // impressions themselves are fine.
        if (reach == null || !reach.available()) {
            return AggregatedMetric.unavailable(reach != null ? reach.unavailableReason() : MetricUnavailableReason.NON_ADDITIVE_AGGREGATION);
        }
        if (impressions == null || !impressions.available()) {
            return AggregatedMetric.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER);
        }
        return divide(impressions.rawValue(), reach.rawValue(), null);
    }

    private static MetricUnavailableReason pickUnavailableReason(AggregatedMetric num, AggregatedMetric denom) {
        if (denom != null && denom.available() && denom.rawValue() != null && denom.rawValue().signum() == 0) {
            return MetricUnavailableReason.DIVISION_BY_ZERO;
        }
        if (num != null && !num.available() && num.unavailableReason() != null) return num.unavailableReason();
        if (denom != null && !denom.available() && denom.unavailableReason() != null) return denom.unavailableReason();
        return MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER;
    }

    /** numerator/denominator (× multiplier); DIVISION_BY_ZERO — never Infinity/NaN/a fabricated 0 — when the denominator is zero. */
    private AggregatedMetric divide(BigDecimal numerator, BigDecimal denominator, BigDecimal multiplier) {
        if (denominator == null || denominator.signum() == 0) {
            return AggregatedMetric.unavailable(MetricUnavailableReason.DIVISION_BY_ZERO);
        }
        BigDecimal result = numerator.divide(denominator, INTERMEDIATE_MATH_CONTEXT);
        if (multiplier != null) result = result.multiply(multiplier);
        return AggregatedMetric.available(result);
    }
}
