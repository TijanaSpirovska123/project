package com.example.marketing.insights.strategy;

import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InsightsFetchStrategy {

    /**
     * The merged result of following every page of a paginated provider response.
     *
     * @param body               the merged response — all pages' rows combined under "data"
     * @param paginationComplete false if a later page (i.e. not the first) failed to fetch, so
     *                           some rows may be missing even though the first page succeeded.
     *                           A first-page failure is NOT represented here — that's a total
     *                           failure and the fetch method throws instead.
     */
    record ProviderFetchResult(Map<String, Object> body, boolean paginationComplete) {}

    /** Single-object fetch, following all pages of the response before returning. */
    ProviderFetchResult fetchForObject(UserEntity user, String objectId, Map<String, String> queryParams);

    /**
     * Batch fetch for multiple object IDs at once. NOTE: currently only follows pagination for
     * the ID-batching itself (chunks of the provider's max IDs per call), not for any single
     * ID's own multi-page result within a chunk — see MetaInsightsFetchStrategy for the documented
     * scope of this gap.
     */
    Map<String, Map<String, Object>> fetchForObjects(UserEntity user, Iterable<String> objectIds, Map<String, String> queryParams);

    /** Account-level fetch, following all pages of the response before returning. */
    ProviderFetchResult fetchForAccount(UserEntity user, String adAccountId, Map<String, String> queryParams);

    List<String> defaultFieldsFor(InsightObjectType objectType, boolean batch);
    
    /**
     * Build query parameters for this provider
     */
    Map<String, String> buildQueryParams(InsightSyncRequestDto request, List<String> fields, int timeIncrement);
    
    /**
     * Determines if a field should be expanded into sub-metrics
     */
    default boolean shouldExpandField(String fieldName) {
        return false;
    }
    
    /**
     * Extracts sub-metrics from a list/array field
     */
    default Map<String, BigDecimal> expandField(String fieldName, List<?> data) {
        return Map.of();
    }
    
    /**
     * Helper method to parse string to BigDecimal
     */
    default BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Locates the raw metric rows within this provider's raw insights response.
     * Each returned element represents one reporting row (typically one day, or the
     * whole requested range collapsed into a single row for account-level/all_days syncs).
     * <p>
     * Every provider has its own response envelope shape (Meta wraps rows under a
     * top-level "data" array; other providers will differ) — no default is provided here
     * deliberately, so a new provider can't silently inherit an unrelated shape assumption.
     */
    List<JsonNode> extractDataRows(JsonNode rawResponse);

    /**
     * Normalizes this row's provider-specific conversion/action data into the shared metric
     * vocabulary: leads, registrations, landingPageViews, addToCart, checkoutInitiated,
     * purchases, purchaseValue, conversions, costPerLead, costPerPurchase, roas.
     * <p>
     * Only includes a key when this row's data could actually determine it — omit rather than
     * fabricate a zero/null value for something this row doesn't address (callers should treat
     * "key absent" the same as any other missing metric).
     * <p>
     * Providers with multiple overlapping ways to report the same underlying event (Meta, e.g.,
     * can report a purchase via several different action types simultaneously) must pick ONE
     * source per metric rather than summing across them, to avoid double-counting.
     */
    Map<String, BigDecimal> normalizeActionMetrics(JsonNode row);
}