package com.example.marketing.insights.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable thresholds for the deterministic FindingEngine (Step 15) — never hard-coded in Java classes. */
@Component
@ConfigurationProperties(prefix = "insights.analytics.findings")
@Data
public class InsightsAnalyticsFindingsProperties {
    private int minimumImpressions = 100;
    private int minimumClicks = 10;
    private int minimumSpend = 5;
    private int percentageChangeThreshold = 10;
    private int highSpendShareThreshold = 40;
}
