package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.util.InsightObjectType;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/** What a given analytics request/aggregation is scoped to — used by strategies to decide capability questions like reach aggregation. */
@Value
@Builder
public class AnalyticsScope {
    InsightObjectType objectType;
    List<String> selectedObjectIds;
    int selectedObjectCount;
    int objectsWithActivity;
    int objectsWithoutActivity;

    public boolean isSingleEntity() {
        return selectedObjectCount == 1;
    }
}
