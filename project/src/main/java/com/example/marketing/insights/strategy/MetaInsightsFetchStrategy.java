package com.example.marketing.insights.strategy;

import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
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
    public Map<String, Object> fetchForObject(UserEntity user, String objectId, Map<String, String> queryParams) {
        String token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);
        ResponseEntity<Map> resp = client.get(objectId + "/insights", queryParams, token);
        return validateAndReturn(resp, "object: " + objectId);
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
    public Map<String, Object> fetchForAccount(UserEntity user, String adAccountId, Map<String, String> queryParams) {
        String token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);
        ResponseEntity<Map> resp = client.get(adAccountId + "/insights", queryParams, token);
        return validateAndReturn(resp, "account: " + adAccountId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndReturn(ResponseEntity<Map> resp, String context) {
        Map<String, Object> body = resp.getBody();
        if (body == null || !resp.getStatusCode().is2xxSuccessful())
            throw new RuntimeException("Meta insights fetch failed [" + context + "]: " + body);
        return body;
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