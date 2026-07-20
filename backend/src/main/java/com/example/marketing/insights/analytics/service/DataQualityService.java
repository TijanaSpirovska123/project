package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.DataQualityIssueDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.FindingSeverity;
import com.example.marketing.insights.util.InsightSyncStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured, deterministic data-quality issues (Step 14) — never a performance judgment.
 * "ROAS unavailable" is reported here as a data-quality limitation; it must never be rephrased
 * as a finding like "campaign has bad ROAS" (that distinction is exactly why this is a separate
 * service from {@code FindingEngine}).
 */
@Service
public class DataQualityService {

    public List<DataQualityIssueDto> analyze(CanonicalDataset dataset) {
        List<DataQualityIssueDto> issues = new ArrayList<>();

        if (dataset.getRecords().isEmpty()) {
            issues.add(DataQualityIssueDto.builder()
                    .code("NO_ACTIVITY_IN_PERIOD")
                    .severity(FindingSeverity.INFO)
                    .message("No delivery data was found for the requested scope and period.")
                    .affectedMetrics(List.of())
                    .build());
        }

        if (dataset.getOverallSyncStatus() == InsightSyncStatus.FAILED) {
            issues.add(DataQualityIssueDto.builder()
                    .code("SYNC_FAILED")
                    .severity(FindingSeverity.CRITICAL)
                    .message("Synchronization failed for one or more requested objects — results may be incomplete or absent.")
                    .affectedMetrics(List.of())
                    .build());
        } else if (dataset.getOverallSyncStatus() == InsightSyncStatus.PARTIALLY_COMPLETE) {
            issues.add(DataQualityIssueDto.builder()
                    .code("PARTIAL_SYNC")
                    .severity(FindingSeverity.WARNING)
                    .message("Some requested objects or pages did not sync successfully — results reflect only what was fetched.")
                    .affectedMetrics(List.of())
                    .build());
        }

        if (dataset.isMixedCurrency()) {
            issues.add(DataQualityIssueDto.builder()
                    .code("MIXED_CURRENCY")
                    .severity(FindingSeverity.WARNING)
                    .message("Selected objects report spend in more than one currency — monetary totals are unavailable rather than silently summed across currencies.")
                    .affectedMetrics(List.of(CanonicalMetric.SPEND, CanonicalMetric.PURCHASE_VALUE, CanonicalMetric.CPC,
                            CanonicalMetric.CPM, CanonicalMetric.COST_PER_LEAD, CanonicalMetric.COST_PER_CONVERSION,
                            CanonicalMetric.COST_PER_PURCHASE, CanonicalMetric.ROAS))
                    .build());
        }

        boolean anyConversionUnavailable = !dataset.getRecords().isEmpty() && dataset.getRecords().stream()
                .noneMatch(r -> r.metric(CanonicalMetric.PURCHASES).isAvailable());
        if (anyConversionUnavailable) {
            issues.add(DataQualityIssueDto.builder()
                    .code("CONVERSION_DATA_UNAVAILABLE")
                    .severity(FindingSeverity.WARNING)
                    .message("Conversion and purchase metrics are unavailable for the selected scope.")
                    .affectedMetrics(List.of(CanonicalMetric.CONVERSIONS, CanonicalMetric.PURCHASES,
                            CanonicalMetric.PURCHASE_VALUE, CanonicalMetric.ROAS))
                    .build());
        }

        boolean anyPurchaseValueUnavailable = !dataset.getRecords().isEmpty() && dataset.getRecords().stream()
                .anyMatch(r -> r.metric(CanonicalMetric.PURCHASES).isAvailable())
                && dataset.getRecords().stream().noneMatch(r -> r.metric(CanonicalMetric.PURCHASE_VALUE).isAvailable());
        if (anyPurchaseValueUnavailable) {
            issues.add(DataQualityIssueDto.builder()
                    .code("PURCHASE_VALUE_UNAVAILABLE")
                    .severity(FindingSeverity.WARNING)
                    .message("Purchases are tracked, but purchase value is not — ROAS cannot be calculated.")
                    .affectedMetrics(List.of(CanonicalMetric.PURCHASE_VALUE, CanonicalMetric.ROAS))
                    .build());
        }

        if (dataset.getScope().getSelectedObjectCount() > 1) {
            issues.add(DataQualityIssueDto.builder()
                    .code("NON_ADDITIVE_REACH")
                    .severity(FindingSeverity.INFO)
                    .message("Reach and frequency are not summed across multiple objects — the same person may be counted more than once.")
                    .affectedMetrics(List.of(CanonicalMetric.REACH, CanonicalMetric.FREQUENCY))
                    .build());
        }

        if (dataset.getObjectNames().size() < dataset.getScope().getSelectedObjectCount()) {
            issues.add(DataQualityIssueDto.builder()
                    .code("MISSING_OBJECT_NAME")
                    .severity(FindingSeverity.INFO)
                    .message("One or more selected objects have no locally-known display name.")
                    .affectedMetrics(List.of())
                    .build());
        }

        return issues;
    }
}
