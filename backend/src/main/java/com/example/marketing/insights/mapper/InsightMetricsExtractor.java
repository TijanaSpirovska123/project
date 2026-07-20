package com.example.marketing.insights.mapper;

import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.strategy.InsightsFetchStrategy;
import com.example.marketing.insights.util.InsightUnavailableReason;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses Meta insight rows into normalized {@link InsightMetricDto} lists, split from raw
 * provider-specific ones so the shared analytics layer can never accidentally double-count
 * a normalized metric alongside its raw per-action-type source(s).
 *
 * Deliberately kept as a plain class rather than default methods on the MapStruct
 * {@code @Mapper} interface: MapStruct auto-detects any mapper-interface method whose
 * signature is (SourceType) -> TargetType as a candidate implicit type-conversion method
 * for any field pair of that same type — a bare String -> String helper here previously got
 * silently applied to unrelated String fields (objectExternalId/adAccountId) during the main
 * entity-to-DTO mapping, NPE-ing on null test/edge-case values before this logic ever ran.
 */
final class InsightMetricsExtractor {

    private InsightMetricsExtractor() {}

    /** The result of extracting one snapshot's metrics: kept structurally separate so callers
     * can never accidentally combine normalizedMetrics and providerMetrics into one sum. */
    record MetricsResult(
            List<InsightMetricDto> normalizedMetrics,
            List<InsightMetricDto> providerMetrics,
            boolean conversionDataAvailable) {

        /** Union of both lists, for backward-compatible consumers of the old combined "metrics" field. */
        List<InsightMetricDto> combined() {
            List<InsightMetricDto> all = new ArrayList<>(normalizedMetrics);
            all.addAll(providerMetrics);
            return all;
        }
    }

    /** Structural/identifier fields Meta includes in every row that are not metrics. */
    private static final Set<String> NON_METRIC_FIELDS = Set.of(
            "date_start", "date_stop",
            "account_id", "account_name", "account_currency",
            "campaign_id", "campaign_name",
            "adset_id", "adset_name",
            "ad_id", "ad_name");

    /**
     * Raw per-action-type expanded fields Meta returns as already-computed ratios/rates (or
     * genuinely non-additive counts like reach). Never summed across multiple daily rows within
     * one stored snapshot — summing or averaging a pre-computed ratio produces a meaningless
     * number. Unlike ctr/cpc/cpm/frequency/roas/costPer* (see RATIO_FORMULAS below), these stay
     * as raw provider detail — there's no single normalized name to recalculate them under (e.g.
     * "cost_per_action_type.landing_page_view" is one of many per-action-type entries), so they
     * simply don't appear when a snapshot spans more than one row.
     */
    private static final Set<String> NON_ADDITIVE_FIELDS = Set.of(
            "reach",
            "unique_ctr", "cpp", "cost_per_unique_click",
            "inline_link_click_ctr", "website_ctr",
            "cost_per_action_type", "cost_per_unique_action_type",
            "purchase_roas", "estimated_ad_recall_rate");

    /** Core, provider-independent base metrics — normalized in spirit even though their raw
     * field name happens to match Meta's own naming. */
    private static final Set<String> BASE_NORMALIZED_NAMES = Set.of("spend", "impressions", "clicks", "reach");

    /** Names produced by InsightsFetchStrategy.normalizeActionMetrics (Step 4) — additive counts/money. */
    private static final Set<String> ACTION_NORMALIZED_NAMES = Set.of(
            "purchases", "purchaseValue", "leads", "registrations",
            "addToCart", "checkoutInitiated", "landingPageViews", "conversions");

    /**
     * Conversion-related metrics that must always appear in normalizedMetrics — either with a
     * real value, or explicitly marked unavailable with a reason — rather than silently omitted.
     * This is the set the "0 vs null" rule applies to most visibly: a consumer must never have
     * to guess whether a missing key here means "zero" or "unknown".
     */
    private static final Set<String> CONVERSION_METRIC_NAMES = Set.of(
            "conversions", "leads", "registrations", "addToCart", "checkoutInitiated",
            "purchases", "purchaseValue", "costPerLead", "costPerConversion", "costPerPurchase",
            "conversionRate", "roas");

    /** One RECALCULATED_RATIO formula: metricName = numerator / denominator (× multiplier), fixed decimal places. */
    private record RatioFormula(String metricName, String numerator, String denominator, BigDecimal multiplier, int scale) {}

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    /**
     * Every ratio metric this module supports, always recalculated fresh from summed totals —
     * whether there's one row or many, and whether the ratio is Meta-native (ctr/cpc/cpm/
     * frequency) or normalized (roas/costPerPurchase/...). Never averaged, never passed through
     * from a provider's raw pre-computed value, so behavior is identical regardless of row count.
     */
    private static final List<RatioFormula> RATIO_FORMULAS = List.of(
            new RatioFormula("ctr", "clicks", "impressions", ONE_HUNDRED, 2),
            new RatioFormula("cpc", "spend", "clicks", null, 2),
            new RatioFormula("cpm", "spend", "impressions", ONE_THOUSAND, 2),
            new RatioFormula("frequency", "impressions", "reach", null, 2),
            new RatioFormula("cpa", "spend", "conversions", null, 2),
            new RatioFormula("costPerLead", "spend", "leads", null, 2),
            new RatioFormula("costPerPurchase", "spend", "purchases", null, 2),
            new RatioFormula("costPerConversion", "spend", "conversions", null, 2),
            new RatioFormula("roas", "purchaseValue", "spend", null, 2),
            new RatioFormula("conversionRate", "conversions", "clicks", ONE_HUNDRED, 2));

    private static final Set<String> RATIO_GOVERNED_NAMES =
            RATIO_FORMULAS.stream().map(RatioFormula::metricName).collect(Collectors.toUnmodifiableSet());

    private static final Set<String> NORMALIZED_NAMES = concat(BASE_NORMALIZED_NAMES, RATIO_GOVERNED_NAMES, ACTION_NORMALIZED_NAMES);

    @SafeVarargs
    private static Set<String> concat(Set<String>... sets) {
        Set<String> result = new java.util.HashSet<>();
        for (Set<String> s : sets) result.addAll(s);
        return Set.copyOf(result);
    }

    /**
     * Extracts metrics from the provider's data rows (one or more rows for the snapshot's
     * stored date range, as located by that provider's InsightsFetchStrategy.extractDataRows).
     * A single row is the common case for account-level/all_days syncs; multiple rows happen
     * whenever a snapshot was synced with a daily time_increment.
     * <p>
     * Additive base metrics (spend, impressions, clicks, purchases, ...) are summed across rows.
     * Every RECALCULATED_RATIO metric (ctr, cpc, cpm, frequency, roas, costPerPurchase, ...) is
     * then computed fresh from those summed totals — never passed through raw or summed itself,
     * whether there's one row or many, so a single-row snapshot and a multi-day one are handled
     * identically rather than one trusting the provider and the other recalculating.
     */
    static MetricsResult extractMetrics(List<JsonNode> rows, InsightsFetchStrategy strategy) {
        // NON_ADDITIVE_FIELDS (reach, cost_per_action_type.*, purchase_roas.*, ...) only pose an
        // aggregation problem when actually combining more than one row — for a single row
        // there's nothing to sum, so the provider's own value passes through unchanged, same as
        // before this step.
        boolean combiningMultipleRows = rows.size() > 1;

        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, String> texts = new LinkedHashMap<>();

        for (JsonNode row : rows) {
            for (InsightMetricDto m : extractRowMetrics(row, strategy)) {
                String name = m.getName();
                if (RATIO_GOVERNED_NAMES.contains(name)) continue; // always recalculated below
                if (combiningMultipleRows && NON_ADDITIVE_FIELDS.contains(baseFieldName(name))) continue;
                if (m.getValueNumber() != null) {
                    sums.merge(name, m.getValueNumber(), BigDecimal::add);
                } else if (m.getValueText() != null) {
                    texts.putIfAbsent(name, m.getValueText());
                }
            }
        }

        for (RatioFormula f : RATIO_FORMULAS) {
            BigDecimal value = safeRatio(sums.get(f.numerator()), sums.get(f.denominator()), f.multiplier(), f.scale());
            if (value != null) sums.put(f.metricName(), value);
        }

        // True only when the provider explicitly reported a purchase-type action for at least
        // one row (even with value 0) — as opposed to no purchase tracking data appearing at
        // all. This is what lets costPerPurchase/roas be null (unavailable) rather than a
        // fabricated 0 when purchase tracking simply isn't present.
        boolean conversionDataAvailable = sums.containsKey("purchases");

        List<InsightMetricDto> normalized = new ArrayList<>();
        List<InsightMetricDto> provider = new ArrayList<>();

        sums.forEach((name, value) -> classify(name, metric(name, value), normalized, provider));
        texts.forEach((name, value) -> {
            if (sums.containsKey(name)) return;
            InsightMetricDto dto = new InsightMetricDto();
            dto.setName(name);
            dto.setValueText(value);
            classify(name, dto, normalized, provider);
        });

        // Guarantee every conversion-related metric is present — either with a real value above,
        // or explicitly marked unavailable with a reason — so a consumer never has to guess
        // whether a missing key means "zero" or "unknown".
        for (String name : CONVERSION_METRIC_NAMES) {
            if (sums.containsKey(name)) continue;
            normalized.add(InsightMetricDto.unavailable(name, unavailableReasonFor(name, sums)));
        }

        return new MetricsResult(normalized, provider, conversionDataAvailable);
    }

    private static void classify(String name, InsightMetricDto dto, List<InsightMetricDto> normalized, List<InsightMetricDto> provider) {
        (NORMALIZED_NAMES.contains(name) ? normalized : provider).add(dto);
    }

    private static String unavailableReasonFor(String name, Map<String, BigDecimal> sums) {
        return switch (name) {
            case "purchaseValue" -> InsightUnavailableReason.PURCHASE_VALUE_UNAVAILABLE;
            case "purchases", "leads", "registrations", "addToCart", "checkoutInitiated", "conversions" ->
                    InsightUnavailableReason.CONVERSION_TRACKING_UNAVAILABLE;
            case "costPerPurchase" -> sums.containsKey("purchases")
                    ? InsightUnavailableReason.DIVISION_BY_ZERO : InsightUnavailableReason.CONVERSION_TRACKING_UNAVAILABLE;
            case "costPerLead" -> sums.containsKey("leads")
                    ? InsightUnavailableReason.DIVISION_BY_ZERO : InsightUnavailableReason.CONVERSION_TRACKING_UNAVAILABLE;
            case "costPerConversion", "conversionRate" -> sums.containsKey("conversions")
                    ? InsightUnavailableReason.DIVISION_BY_ZERO : InsightUnavailableReason.CONVERSION_TRACKING_UNAVAILABLE;
            case "roas" -> sums.containsKey("purchaseValue")
                    ? InsightUnavailableReason.DIVISION_BY_ZERO : InsightUnavailableReason.PURCHASE_VALUE_UNAVAILABLE;
            default -> InsightUnavailableReason.NOT_RETURNED_BY_PROVIDER;
        };
    }

    /**
     * numerator / denominator (× multiplier if given), rounded to scale decimal places; null if
     * the denominator is zero or either operand is absent — division by zero is undefined, never
     * a fabricated 0, Infinity or NaN.
     */
    private static BigDecimal safeRatio(BigDecimal numerator, BigDecimal denominator, BigDecimal multiplier, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null;
        BigDecimal result = numerator.divide(denominator, scale + 4, RoundingMode.HALF_UP);
        if (multiplier != null) result = result.multiply(multiplier);
        return result.setScale(scale, RoundingMode.HALF_UP);
    }

    private static InsightMetricDto metric(String name, BigDecimal value) {
        InsightMetricDto dto = new InsightMetricDto();
        dto.setName(name);
        dto.setValueNumber(value);
        return dto;
    }

    /** Raw pass-through fields plus this provider's normalized action/conversion metrics for one row. */
    private static List<InsightMetricDto> extractRowMetrics(JsonNode rowNode, InsightsFetchStrategy strategy) {
        List<InsightMetricDto> result = extractMetricsFromRow(rowNode);
        strategy.normalizeActionMetrics(rowNode).forEach((name, value) -> result.add(metric(name, value)));
        return result;
    }

    /** Extracts raw pass-through metrics from a single Meta insights row (one day, or one account/all_days total). */
    static List<InsightMetricDto> extractMetricsFromRow(JsonNode rowNode) {
        List<InsightMetricDto> result = new ArrayList<>();

        rowNode.fields().forEachRemaining(entry -> {
            if (NON_METRIC_FIELDS.contains(entry.getKey())) return;
            // ctr/cpc/cpm/frequency are always recalculated (see RATIO_FORMULAS) — never passed
            // through raw, so there's exactly one value under each name regardless of row count.
            if (RATIO_GOVERNED_NAMES.contains(entry.getKey())) return;

            JsonNode valueNode = entry.getValue();

            // Expandable array fields (actions, action_values, cost_per_action_type, purchase_roas, etc.)
            if (valueNode.isArray()) {
                for (JsonNode item : valueNode) {
                    String actionType = item.path("action_type").asText(null);
                    String value = item.path("value").asText(null);
                    if (actionType != null && value != null) {
                        InsightMetricDto dto = new InsightMetricDto();
                        dto.setName(entry.getKey() + "." + actionType);
                        dto.setValueNumber(parseDecimal(value));
                        result.add(dto);
                    }
                }
                return;
            }

            // Flat numeric/text fields
            if (valueNode.isNumber() || valueNode.isTextual()) {
                InsightMetricDto dto = new InsightMetricDto();
                dto.setName(entry.getKey());
                if (valueNode.isNumber()) {
                    dto.setValueNumber(valueNode.decimalValue());
                } else {
                    BigDecimal parsed = parseDecimal(valueNode.asText());
                    if (parsed != null) dto.setValueNumber(parsed);
                    else dto.setValueText(valueNode.asText());
                }
                result.add(dto);
            }
        });

        return result;
    }

    private static String baseFieldName(String metricName) {
        int dot = metricName.indexOf('.');
        return dot >= 0 ? metricName.substring(0, dot) : metricName;
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * Derives the date range actually covered by DELIVERY rows (rows with real activity) from
     * their own date_start/date_stop fields — this is the activityPeriod, not a completeness
     * signal. The provider's own rows are ground truth for what delivery actually occurred;
     * absence of rows for other days in the requested range means no delivery those days, not
     * incomplete synchronization. Returns null if no row had a parseable date_start (e.g. a
     * custom field list omitted it, or there were no rows at all).
     */
    static InsightPeriodDto computeActivityPeriod(List<JsonNode> rows) {
        LocalDate minStart = null;
        LocalDate maxStop = null;

        for (JsonNode row : rows) {
            LocalDate start = parseLocalDate(row.path("date_start").asText(null));
            if (start == null) continue;
            LocalDate stop = parseLocalDate(row.path("date_stop").asText(null));
            if (stop == null) stop = start;

            if (minStart == null || start.isBefore(minStart)) minStart = start;
            if (maxStop == null || stop.isAfter(maxStop)) maxStop = stop;
        }

        return minStart == null ? null : new InsightPeriodDto(minStart, maxStop);
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (Exception e) { return null; }
    }
}
