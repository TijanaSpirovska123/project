package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.util.InsightSyncStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * The ONE reusable, provider-independent dataset every analytics service (summary, time-series,
 * comparison, rankings, breakdown, data-quality, findings, AnalysisContext) is built from. Loaded
 * exactly once per request by {@code CanonicalDatasetLoader} — snapshots are fetched and rawJson
 * is parsed exactly once, currency/ownership/sync-status are resolved exactly once, and every
 * consuming service reads this same in-memory dataset instead of independently re-querying or
 * re-parsing. This is also the interface boundary that lets a future canonical fact-table
 * replace snapshot-parsing: as long as a loader implementation still produces a CanonicalDataset,
 * every analytics service downstream needs zero changes.
 */
@Value
@Builder
public class CanonicalDataset {
    Provider provider;
    String adAccountId;
    InsightPeriodDto requestedPeriod;
    AnalyticsScope scope;

    List<CanonicalInsightRecord> records;

    /**
     * Internal-only: the same snapshot entities already fetched to build {@code records}, kept so
     * breakdown-by-dimension computation (which needs each snapshot's stored breakdownsJson/rawJson,
     * not just normalized metrics) can reuse this ONE fetch instead of re-querying the repository —
     * see BreakdownAnalyticsService.computeBreakdown. Never serialized into any response DTO.
     */
    List<InsightSnapshotEntity> snapshotEntities;

    /** externalId -> display name, batch-loaded once (never N+1). Missing entries mean the name lookup didn't find a locally-synced entity (deleted, or never synced). */
    Map<String, String> objectNames;

    /** Single account currency, or null when mixed/unknown. */
    String currency;
    boolean mixedCurrency;

    InsightSyncStatus overallSyncStatus;
    boolean overallSyncComplete;

    List<InsightWarningDto> warnings;

    /** Present only when currency could not be determined at all (distinct from genuinely mixed). */
    MetricUnavailableReason currencyUnavailableReason;
}
