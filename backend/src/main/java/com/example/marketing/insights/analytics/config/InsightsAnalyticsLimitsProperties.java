package com.example.marketing.insights.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable safety limits for every Phase-2 analytics endpoint (Step 2 / Step 22). */
@Component
@ConfigurationProperties(prefix = "insights.analytics.limits")
@Data
public class InsightsAnalyticsLimitsProperties {
    private int maxSelectedCampaigns = 200;
    private int maxSelectedAdSets = 200;
    private int maxSelectedAds = 200;
    private int maxDateRangeDays = 400;
    private int maxTimeSeriesBuckets = 400;
    private int maxRankingResults = 100;
}
