package com.example.marketing.insights.strategy;

import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class MetaInsightsFetchStrategy implements InsightsFetchStrategy {

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
            "actions", "unique_actions", "action_values", "impressions", "clicks",
            "unique_clicks", "spend", "frequency", "inline_link_clicks",
            "inline_post_engagement", "reach", "website_ctr",
            "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions",
            "video_p75_watched_actions", "video_p95_watched_actions",
            "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_play_actions", "video_continuous_2_sec_watched_actions",
            "unique_video_continuous_2_sec_watched_actions",
            "estimated_ad_recallers", "estimated_ad_recall_rate",
            "unique_outbound_clicks", "outbound_clicks",
            "conversions", "conversion_values", "social_spend");

    public static final List<String> AD_FIELDS = List.of(
            "ad_id", "clicks", "impressions", "spend", "outbound_clicks",
            "actions", "action_values", "cost_per_unique_action_type", "reach");

    public static final List<String> ADSET_FIELDS = List.of(
            "impressions", "clicks", "spend", "reach", "frequency",
            "cpm", "cpc", "ctr", "actions", "action_values");

    public static final List<String> CAMPAIGN_FIELDS = List.of(
            "impressions", "clicks", "conversions", "cpc", "cpm", "cpp",
            "ctr", "frequency", "reach", "social_spend",
            "video_play_actions", "spend", "account_currency");

    public static final List<String> ACCOUNT_FIELDS = List.of(
            "impressions", "clicks", "conversions", "cpc", "cpm", "cpp",
            "ctr", "frequency", "reach", "social_spend",
            "video_play_actions", "spend", "account_currency",
            "account_id", "account_name", "actions", "unique_actions",
            "action_values", "cost_per_action_type", "cost_per_unique_action_type",
            "inline_link_clicks", "unique_inline_link_clicks",
            "inline_post_engagement", "cost_per_inline_link_click",
            "cost_per_inline_post_engagement", "outbound_clicks",
            "unique_outbound_clicks", "website_ctr",
            "video_thruplay_watched_actions", "video_avg_time_watched_actions",
            "video_p25_watched_actions", "video_p50_watched_actions",
            "video_p75_watched_actions", "video_p95_watched_actions",
            "video_p100_watched_actions", "video_30_sec_watched_actions",
            "video_continuous_2_sec_watched_actions",
            "unique_video_continuous_2_sec_watched_actions");

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

        String ids = StreamSupport.stream(objectIds.spliterator(), false)
                .collect(Collectors.joining(","));

        Map<String, String> q = new HashMap<>(queryParams);
        q.put("ids", ids);

        ResponseEntity<Map> resp = client.get("insights", q, token);
        Map<String, Object> body = validateAndReturn(resp, "batch ids=" + ids);

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var entry : body.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> perObject) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) perObject;
                result.put(entry.getKey(), typed);
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