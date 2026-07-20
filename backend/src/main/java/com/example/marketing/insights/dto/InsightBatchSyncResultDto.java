package com.example.marketing.insights.dto;

import com.example.marketing.insights.util.InsightSyncStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a batch sync (POST /sync/{campaigns,adsets,ads}/batch) — distinguishes entities
 * that synced with real delivery data, entities that synced successfully but had no delivery,
 * and entities that genuinely failed to sync, rather than reporting only a flat list of results.
 */
@Data
public class InsightBatchSyncResultDto {
    private int requestedCount;
    private int processedCount;
    private int successfulCount;
    private int emptyResultCount;
    private int failedCount;

    private InsightSyncStatus syncStatus;

    /** Same shape as the old bare-array response, for any consumer still reading a flat snapshot list. */
    private List<InsightSnapshotDto> snapshots = new ArrayList<>();

    private List<InsightEntitySyncResultDto> results = new ArrayList<>();
}
