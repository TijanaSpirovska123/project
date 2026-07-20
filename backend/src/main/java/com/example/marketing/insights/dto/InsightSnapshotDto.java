package com.example.marketing.insights.dto;

import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.infrastructure.util.Provider;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InsightSnapshotDto {
    private Long id;
    private Provider provider;
    private String adAccountId;

    private InsightObjectType objectType;
    private String objectExternalId;

    /**
     * requestedPeriod, in the existing field names — the range this snapshot was synced/
     * requested for. Inclusive on both ends. Kept as-is for backward compatibility; this is
     * NOT the range where delivery actually occurred (see activityPeriod).
     */
    private LocalDate dateStart;
    private LocalDate dateStop;
    private Integer timeIncrement;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Combined normalized + provider-specific metrics, kept for backward compatibility with
     * existing consumers. New code should read normalizedMetrics/providerMetrics instead —
     * this list is their union and must never be summed/aggregated as a whole (a provider
     * metric like actions.landing_page_view coexisting with normalized landingPageViews would
     * double-count if both were added together).
     */
    private List<InsightMetricDto> metrics;

    /**
     * Metrics in the shared, provider-independent vocabulary (ctr, cpc, purchases, roas, ...).
     * The ONLY metrics the shared analytics layer (comparisons, findings, AI context) may use.
     */
    private List<InsightMetricDto> normalizedMetrics;

    /**
     * Raw provider-specific metrics (actions.purchase, cost_per_action_type.lead, ...), kept for
     * debugging/audit. Must never be automatically aggregated together with normalizedMetrics —
     * several of these are alternate/overlapping representations of the same normalized metric.
     */
    private List<InsightMetricDto> providerMetrics;

    private Object rawData;

    /**
     * The range actually covered by delivery data (rows with real activity) that the provider
     * returned, derived from the min/max date_start/date_stop of those rows. Null when there was
     * no delivery at all, or when it couldn't be determined (e.g. a custom field list omitted
     * date_start/date_stop). This is NOT a completeness signal — a request can be fully and
     * successfully synced while activityPeriod is much narrower than dateStart/dateStop, simply
     * because the campaign didn't deliver every day. See syncComplete/syncStatus for whether the
     * sync itself succeeded.
     */
    private InsightPeriodDto activityPeriod;

    /** Number of rows (days) with actual delivery data returned by the provider. 0 when activityPeriod is null. */
    private Integer daysWithActivity;

    /**
     * Whether the backend successfully fetched and processed the COMPLETE requested period —
     * based on request execution, pagination, and persistence, never on whether delivery
     * occurred throughout the range. True even when daysWithActivity is 0 or much smaller than
     * the requested range's day count.
     */
    private boolean syncComplete = true;

    private InsightSyncStatus syncStatus = InsightSyncStatus.COMPLETE;

    /**
     * Whether purchase/conversion tracking data was present at all for this snapshot (regardless
     * of the actual purchase count/value) — null when not applicable (e.g. no purchase-related
     * metrics were requested at all). False means purchases/purchaseValue/roas/costPerPurchase
     * are unavailable (null), not necessarily zero; true means they reflect real tracked values,
     * including real zeros.
     */
    private Boolean conversionDataAvailable;

    /**
     * Explains why metrics/rawData may be empty (or why extraction failed), instead of
     * silently returning empty results that look identical to genuine zero-activity data.
     * Empty (not null) when nothing is wrong.
     */
    private List<InsightWarningDto> warnings = new java.util.ArrayList<>();

}
