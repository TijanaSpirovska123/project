package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.user.entity.UserEntity;

/**
 * Loads a {@link CanonicalDataset} exactly once per analytics request. This is the interface
 * boundary Step 6/Step 10 call for: today {@code SnapshotCanonicalDatasetLoader} builds the
 * dataset by querying {@code InsightSnapshotRepository} and parsing each snapshot's stored
 * rawJson (via the existing Phase-1 mapper) exactly once per snapshot. If a canonical daily fact
 * table is introduced later, only a new implementation of THIS interface needs to be written —
 * every summary/time-series/comparison/ranking/breakdown/finding/data-quality service downstream
 * consumes {@link CanonicalDataset} and would need no changes.
 */
public interface CanonicalDatasetLoader {
    CanonicalDataset load(UserEntity user, AnalyticsFilterRequest request);
}
