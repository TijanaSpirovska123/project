package com.example.marketing.insights.strategy;

import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaInsightsFetchStrategy implements InsightsFetchStrategy {

    private static final int META_BATCH_ID_LIMIT = 50;

    private final PlatformClientRegistry clients;
    private final TokenService tokens;

    // -------------------------------------------------------------------------
    // Expandable fields for Meta platform
    // -------------------------------------------------------------------------

    private static final Set<String> EXPANDABLE_FIELDS = Set.of(
            "actions",
            "unique_actions",
            "action_values",
            "cost_per_action_type",
            "cost_per_unique_action_type",
            "outbound_clicks",
            "unique_outbound_clicks",
            "website_ctr",
            "purchase_roas",
            "cost_per_inline_link_click",
            "cost_per_inline_post_engagement",
            "video_play_actions",
            "video_thruplay_watched_actions",
            "video_avg_time_watched_actions",
            "video_p25_watched_actions",
            "video_p50_watched_actions",
            "video_p75_watched_actions",
            "video_p95_watched_actions",
            "video_p100_watched_actions",
            "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions",
            "unique_video_continuous_2_sec_watched_actions");

    // -------------------------------------------------------------------------
    // Default fields per object type — Meta-specific
    // -------------------------------------------------------------------------

    public static final List<String> AD_BATCH_FIELDS = List.of(
            "date_start", "date_stop", "account_id", "account_name",
            "ad_id", "ad_name", "adset_id", "adset_name", "campaign_id", "campaign_name",
            // Core
            "impressions", "reach", "frequency", "clicks", "unique_clicks",
            "spend", "cpm", "cpc", "ctr", "unique_ctr", "cpp", "cost_per_unique_click", "social_spend",
            // Engagement
            "inline_link_clicks", "inline_post_engagement", "unique_inline_link_clicks",
            "inline_link_click_ctr", "outbound_clicks", "unique_outbound_clicks", "website_ctr",
            // Video
            "video_play_actions", "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions", "video_p75_watched_actions",
            "video_p95_watched_actions", "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions", "unique_video_continuous_2_sec_watched_actions",
            // Conversions / Actions
            "actions", "unique_actions", "action_values",
            "cost_per_action_type", "cost_per_unique_action_type",
            "conversions", "conversion_values", "purchase_roas", "cost_per_conversion",
            // Estimated
            "estimated_ad_recallers", "estimated_ad_recall_rate");

    public static final List<String> AD_FIELDS = List.of(
            // Core
            "impressions", "reach", "frequency", "clicks", "unique_clicks",
            "spend", "cpm", "cpc", "ctr", "unique_ctr", "cpp", "cost_per_unique_click", "social_spend",
            // Engagement
            "inline_link_clicks", "inline_post_engagement", "unique_inline_link_clicks",
            "inline_link_click_ctr", "outbound_clicks", "unique_outbound_clicks", "website_ctr",
            // Video
            "video_play_actions", "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions", "video_p75_watched_actions",
            "video_p95_watched_actions", "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions",
            // Conversions / Actions
            "actions", "unique_actions", "action_values",
            "cost_per_action_type", "cost_per_unique_action_type",
            "conversions", "conversion_values", "purchase_roas", "cost_per_conversion",
            // Estimated
            "estimated_ad_recall_rate", "estimated_ad_recallers",
            // Date
            "date_start", "date_stop");

    public static final List<String> ADSET_FIELDS = List.of(
            // Core
            "impressions", "reach", "frequency", "clicks", "unique_clicks",
            "spend", "cpm", "cpc", "ctr", "unique_ctr", "cpp", "cost_per_unique_click", "social_spend",
            // Engagement
            "inline_link_clicks", "inline_post_engagement", "unique_inline_link_clicks",
            "inline_link_click_ctr", "outbound_clicks", "unique_outbound_clicks", "website_ctr",
            // Video
            "video_play_actions", "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions", "video_p75_watched_actions",
            "video_p95_watched_actions", "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions",
            // Conversions / Actions
            "actions", "unique_actions", "action_values",
            "cost_per_action_type", "cost_per_unique_action_type",
            "conversions", "conversion_values", "purchase_roas", "cost_per_conversion",
            // Estimated
            "estimated_ad_recall_rate", "estimated_ad_recallers",
            // Date
            "date_start", "date_stop");

    public static final List<String> CAMPAIGN_FIELDS = List.of(
            // Core
            "impressions", "reach", "frequency", "clicks", "unique_clicks",
            "spend", "cpm", "cpc", "ctr", "unique_ctr", "cpp", "cost_per_unique_click", "social_spend",
            // Engagement
            "inline_link_clicks", "inline_post_engagement", "unique_inline_link_clicks",
            "inline_link_click_ctr", "outbound_clicks", "unique_outbound_clicks", "website_ctr",
            // Video
            "video_play_actions", "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions", "video_p75_watched_actions",
            "video_p95_watched_actions", "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions",
            // Conversions / Actions
            "actions", "unique_actions", "action_values",
            "cost_per_action_type", "cost_per_unique_action_type",
            "conversions", "conversion_values", "purchase_roas", "cost_per_conversion",
            // Estimated
            "estimated_ad_recall_rate", "estimated_ad_recallers",
            // Date / Account
            "account_currency", "date_start", "date_stop");

    public static final List<String> ACCOUNT_FIELDS = List.of(
            // Core
            "impressions", "reach", "frequency", "clicks", "unique_clicks",
            "spend", "cpm", "cpc", "ctr", "unique_ctr", "cpp", "cost_per_unique_click", "social_spend",
            // Engagement
            "inline_link_clicks", "inline_post_engagement", "unique_inline_link_clicks",
            "inline_link_click_ctr", "cost_per_inline_link_click", "cost_per_inline_post_engagement",
            "outbound_clicks", "unique_outbound_clicks", "website_ctr",
            // Video
            "video_play_actions", "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions", "video_p75_watched_actions",
            "video_p95_watched_actions", "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions", "unique_video_continuous_2_sec_watched_actions",
            // Conversions / Actions
            "actions", "unique_actions", "action_values",
            "cost_per_action_type", "cost_per_unique_action_type",
            "conversions", "conversion_values", "purchase_roas", "cost_per_conversion",
            // Estimated
            "estimated_ad_recall_rate", "estimated_ad_recallers",
            // Account info
            "account_id", "account_name", "account_currency");

    // -------------------------------------------------------------------------
    // Default fields lookup by object type
    // -------------------------------------------------------------------------

    @Override
    public List<String> defaultFieldsFor(InsightObjectType objectType, boolean batch) {
        if (objectType == null)
            return AD_FIELDS;
        return switch (objectType) {
            case ACCOUNT -> ACCOUNT_FIELDS;
            case CAMPAIGN -> CAMPAIGN_FIELDS;
            case ADSET -> ADSET_FIELDS;
            case AD -> batch ? AD_BATCH_FIELDS : AD_FIELDS;
        };
    }

    // -------------------------------------------------------------------------
    // Metric expansion for Meta platform
    // -------------------------------------------------------------------------

    @Override
    public boolean shouldExpandField(String fieldName) {
        return EXPANDABLE_FIELDS.contains(fieldName);
    }

    @Override
    public Map<String, BigDecimal> expandField(String fieldName, List<?> data) {
        Map<String, BigDecimal> result = new HashMap<>();

        for (Object item : data) {
            if (!(item instanceof Map<?, ?> map))
                continue;

            // Meta format: [{"action_type": "link_click", "value": "18"}]
            Object typeKey = map.get("action_type");
            Object valueKey = map.get("value");

            if (typeKey != null && valueKey != null) {
                String metricKey = fieldName + "." + typeKey;
                BigDecimal numValue = parseDecimal(String.valueOf(valueKey));
                if (numValue != null) {
                    result.put(metricKey, numValue);
                }
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Fetch methods
    // -------------------------------------------------------------------------

    @Override
    public ProviderFetchResult fetchForObject(UserEntity user, String objectId, Map<String, String> queryParams) {
        String token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);
        return fetchAllPages(client, objectId + "/insights", queryParams, token, "object: " + objectId);
    }

    @Override
    public Map<String, Map<String, Object>> fetchForObjects(UserEntity user, Iterable<String> objectIds,
            Map<String, String> queryParams) {
        String token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);

        List<String> allIds = StreamSupport.stream(objectIds.spliterator(), false)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        int totalBatches = (int) Math.ceil((double) allIds.size() / META_BATCH_ID_LIMIT);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        for (int i = 0; i < allIds.size(); i += META_BATCH_ID_LIMIT) {
            List<String> chunk = allIds.subList(i, Math.min(i + META_BATCH_ID_LIMIT, allIds.size()));
            String ids = String.join(",", chunk);

            log.info("Fetching Meta insights batch {}/{} ({} IDs)",
                    (i / META_BATCH_ID_LIMIT) + 1, totalBatches, chunk.size());

            Map<String, String> q = new HashMap<>(queryParams);
            q.put("ids", ids);

            ResponseEntity<Map> resp = client.get("insights", q, token);
            Map<String, Object> body = validateAndReturn(resp, "batch ids=" + ids);

            for (var entry : body.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> perObject) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) perObject;
                    result.put(entry.getKey(), typed);
                }
            }
        }

        return result;
    }

    @Override
    public ProviderFetchResult fetchForAccount(UserEntity user, String adAccountId, Map<String, String> queryParams) {
        String token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);
        return fetchAllPages(client, adAccountId + "/insights", queryParams, token, "account: " + adAccountId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndReturn(ResponseEntity<Map> resp, String context) {
        Map<String, Object> body = resp.getBody();
        if (body == null || !resp.getStatusCode().is2xxSuccessful())
            throw new RuntimeException("Meta insights fetch failed [" + context + "]: " + body);
        return body;
    }

    /** Safety cap against a misbehaving/infinite cursor loop — far beyond any real insights query. */
    private static final int MAX_PAGES = 200;

    /**
     * Follows every page of a Meta insights response (via paging.cursors.after), merging all
     * rows into one combined "data" array. A failure on the FIRST page is a total failure and
     * propagates as an exception (unchanged existing behavior); a failure on a LATER page keeps
     * whatever was already fetched and marks the result as pagination-incomplete rather than
     * silently discarding it or claiming full success.
     */
    @SuppressWarnings("unchecked")
    private ProviderFetchResult fetchAllPages(com.example.marketing.infrastructure.strategy.PlatformClient client,
            String path, Map<String, String> queryParams, String token, String context) {

        List<Object> allRows = new ArrayList<>();
        Map<String, Object> lastBody = null;
        Map<String, String> params = new HashMap<>(queryParams);
        int pagesFetched = 0;
        boolean complete = true;
        String after = null;

        do {
            if (after != null) params.put("after", after);

            Map<String, Object> body;
            try {
                ResponseEntity<Map> resp = client.get(path, params, token);
                body = validateAndReturn(resp, context + " (page " + (pagesFetched + 1) + ")");
            } catch (RuntimeException e) {
                if (pagesFetched == 0) throw e; // first page failing is a total failure, not partial
                log.warn("Pagination stopped after {} page(s) for [{}]: {}", pagesFetched, context, e.getMessage());
                complete = false;
                break;
            }

            pagesFetched++;
            lastBody = body;

            Object dataObj = body.get("data");
            if (dataObj instanceof List<?> dataList) allRows.addAll(dataList);

            Map<String, Object> paging = (Map<String, Object>) body.get("paging");
            Map<String, Object> cursors = paging != null ? (Map<String, Object>) paging.get("cursors") : null;
            after = cursors != null ? (String) cursors.get("after") : null;

            if (after != null && pagesFetched >= MAX_PAGES) {
                log.warn("Pagination cap ({} pages) reached for [{}] — stopping to avoid an infinite cursor loop", MAX_PAGES, context);
                complete = false;
                break;
            }
        } while (after != null);

        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("data", allRows);
        if (lastBody != null && lastBody.containsKey("paging")) merged.put("paging", lastBody.get("paging"));
        return new ProviderFetchResult(merged, complete);
    }

    /**
     * Meta's /insights response is always the envelope {"data": [...rows...], "paging": {...}}.
     * The actual metric fields (spend, impressions, actions, ...) live inside each element of
     * "data", never at the top level of the envelope itself.
     */
    @Override
    public List<JsonNode> extractDataRows(JsonNode rawResponse) {
        JsonNode dataNode = rawResponse.path("data");
        if (!dataNode.isArray()) return List.of();
        List<JsonNode> rows = new ArrayList<>();
        dataNode.forEach(rows::add);
        return rows;
    }

    // -------------------------------------------------------------------------
    // Action-type normalization
    //
    // Meta can report the SAME underlying conversion via several overlapping action types at
    // once — e.g. Meta's own docs describe "omni_purchase" as a grouped/deduplicated total
    // across channel-specific purchase types (offsite_conversion.fb_pixel_purchase,
    // app_custom_event.fb_mobile_purchase, onsite_conversion.purchase, ...). Summing all of
    // them would double- or triple-count the same purchases. Each list below is a priority
    // order: the first action_type found wins, and no others are added on top of it.
    // -------------------------------------------------------------------------

    private static final List<String> PURCHASE_ACTION_TYPES = List.of(
            "omni_purchase", "purchase", "offsite_conversion.fb_pixel_purchase",
            "onsite_conversion.purchase", "app_custom_event.fb_mobile_purchase");

    // "_grouped" action types follow the same "grouped/deduplicated total across channels"
    // pattern Meta documents for omni_purchase — preferred first, same reasoning: never sum
    // a grouped total together with the channel-specific types it already aggregates.
    private static final List<String> LEAD_ACTION_TYPES = List.of(
            "onsite_conversion.lead_grouped", "lead", "offsite_conversion.fb_pixel_lead");

    private static final List<String> REGISTRATION_ACTION_TYPES = List.of(
            "complete_registration", "offsite_conversion.fb_pixel_complete_registration");

    private static final List<String> ADD_TO_CART_ACTION_TYPES = List.of(
            "add_to_cart", "offsite_conversion.fb_pixel_add_to_cart");

    private static final List<String> CHECKOUT_ACTION_TYPES = List.of(
            "initiate_checkout", "offsite_conversion.fb_pixel_initiate_checkout");

    private static final List<String> LANDING_PAGE_VIEW_ACTION_TYPES = List.of(
            "omni_landing_page_view", "landing_page_view");

    /**
     * Normalizes only the additive (SUM-strategy) conversion metrics: counts and monetary
     * totals. Ratio metrics derived from these (costPerPurchase, costPerLead, roas,
     * costPerConversion, conversionRate, cpa) are deliberately NOT computed here — they're
     * always recalculated fresh from summed totals by InsightMetricsExtractor, uniformly for
     * both Meta-native ratios (ctr, cpc, cpm, frequency) and these normalized ones. Passing one
     * row's raw cost_per_action_type/purchase_roas through directly here would let it collide
     * with — or get silently summed alongside — that recalculated value.
     */
    @Override
    public Map<String, BigDecimal> normalizeActionMetrics(JsonNode row) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        String winningPurchaseType = firstAvailableActionType(row, "actions", PURCHASE_ACTION_TYPES);
        if (winningPurchaseType != null) {
            putIfPresent(result, "purchases", actionValueFor(row, "actions", winningPurchaseType));
            putIfPresent(result, "purchaseValue", actionValueFor(row, "action_values", winningPurchaseType));
        }

        putIfPresent(result, "leads", firstAvailableActionValue(row, "actions", LEAD_ACTION_TYPES));
        putIfPresent(result, "registrations", firstAvailableActionValue(row, "actions", REGISTRATION_ACTION_TYPES));
        putIfPresent(result, "addToCart", firstAvailableActionValue(row, "actions", ADD_TO_CART_ACTION_TYPES));
        putIfPresent(result, "checkoutInitiated", firstAvailableActionValue(row, "actions", CHECKOUT_ACTION_TYPES));
        putIfPresent(result, "landingPageViews", firstAvailableActionValue(row, "actions", LANDING_PAGE_VIEW_ACTION_TYPES));

        // Meta's own "conversions" field is itself already an aggregate across the account's
        // configured custom conversions — sum its entries rather than picking one.
        putIfPresent(result, "conversions", sumArrayField(row, "conversions"));

        return result;
    }

    private static String firstAvailableActionType(JsonNode row, String arrayField, List<String> priorityOrder) {
        JsonNode array = row.path(arrayField);
        if (!array.isArray()) return null;
        for (String candidate : priorityOrder) {
            for (JsonNode item : array) {
                if (candidate.equals(item.path("action_type").asText(null))) return candidate;
            }
        }
        return null;
    }

    private BigDecimal actionValueFor(JsonNode row, String arrayField, String actionType) {
        JsonNode array = row.path(arrayField);
        if (!array.isArray()) return null;
        for (JsonNode item : array) {
            if (actionType.equals(item.path("action_type").asText(null))) {
                return parseDecimal(item.path("value").asText(null));
            }
        }
        return null;
    }

    private BigDecimal firstAvailableActionValue(JsonNode row, String arrayField, List<String> priorityOrder) {
        String winner = firstAvailableActionType(row, arrayField, priorityOrder);
        return winner == null ? null : actionValueFor(row, arrayField, winner);
    }

    private BigDecimal sumArrayField(JsonNode row, String arrayField) {
        JsonNode array = row.path(arrayField);
        if (!array.isArray() || array.isEmpty()) return null;
        BigDecimal sum = null;
        for (JsonNode item : array) {
            BigDecimal v = parseDecimal(item.path("value").asText(null));
            if (v == null) continue;
            sum = (sum == null ? BigDecimal.ZERO : sum).add(v);
        }
        return sum;
    }

    private static void putIfPresent(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) map.put(key, value);
    }

    @Override

    public Map<String, String> buildQueryParams(InsightSyncRequestDto request, List<String> fields, int timeIncrement) {
        Map<String, String> params = new HashMap<>();
        addFieldsParam(params, fields);
        addDateParams(params, request);
        addTimeIncrementParam(params, request, timeIncrement);
        addLimitParam(params, request);
        addActionBreakdownsParam(params, request);
        addActionReportTimeParam(params, request);
        addCustomBreakdownsParam(params, request);
        return params;
    }

    private void addFieldsParam(Map<String, String> params, List<String> fields) {
        if (fields != null && !fields.isEmpty()) {
            params.put("fields", String.join(",", fields));
        }
    }

    // ...existing code...

    private void addDateParams(Map<String, String> params, InsightSyncRequestDto request) {
        if (request.getDatePreset() != null && !request.getDatePreset().isBlank()) {
            params.put("date_preset", request.getDatePreset());
        } else if (request.getDateStart() != null && request.getDateStop() != null) {
            // Pass raw JSON — UriComponentsBuilder.build() in MetaPlatformClient encodes it once
            params.put("time_range", String.format(
                    "{\"since\":\"%s\",\"until\":\"%s\"}",
                    request.getDateStart(),
                    request.getDateStop()));
        }
    }

    private void addTimeIncrementParam(Map<String, String> params, InsightSyncRequestDto request, int timeIncrement) {
        if (Boolean.TRUE.equals(request.getTimeIncrementAllDays())) {
            params.put("time_increment", "all_days");
        } else if (timeIncrement > 0) {
            params.put("time_increment", String.valueOf(timeIncrement));
        }
    }

    private void addLimitParam(Map<String, String> params, InsightSyncRequestDto request) {
        if (request.getLimit() != null && request.getLimit() > 0) {
            params.put("limit", String.valueOf(request.getLimit()));
        }
    }

    private void addActionBreakdownsParam(Map<String, String> params, InsightSyncRequestDto request) {
        if (request.getActionBreakdowns() != null && !request.getActionBreakdowns().isBlank()) {
            params.put("action_breakdowns", request.getActionBreakdowns());
        }
    }

    private void addActionReportTimeParam(Map<String, String> params, InsightSyncRequestDto request) {
        if (request.getActionReportTime() != null && !request.getActionReportTime().isBlank()) {
            params.put("action_report_time", request.getActionReportTime());
        }
    }

    private void addCustomBreakdownsParam(Map<String, String> params, InsightSyncRequestDto request) {
        if (request.getBreakdowns() == null || request.getBreakdowns().isEmpty()) {
            return;
        }

        Set<String> reserved = Set.of(
                "fields", "date_preset", "time_range", "time_increment",
                "limit", "action_breakdowns", "action_report_time");

        request.getBreakdowns().forEach((key, value) -> {
            if (key == null || reserved.contains(key))
                return;
            params.put(key, String.valueOf(value));
        });
    }

}