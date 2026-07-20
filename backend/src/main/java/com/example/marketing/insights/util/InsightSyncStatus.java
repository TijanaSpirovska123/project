package com.example.marketing.insights.util;

/**
 * Whether a sync operation (or the persisted result of one) actually completed successfully —
 * based on request execution, pagination, and persistence, NOT on whether the provider
 * returned delivery data for every day of the requested range. A campaign with zero delivery
 * for 6 of 7 requested months is still COMPLETE if the request itself succeeded fully.
 */
public enum InsightSyncStatus {
    /** The full requested interval was fetched (all pages followed) and persisted successfully. */
    COMPLETE,
    /** Some, but not all, of the request succeeded (e.g. one page failed, or some batch entities failed). */
    PARTIALLY_COMPLETE,
    /** The request failed entirely — no usable data was fetched or persisted. */
    FAILED,
    /** Nothing has been synced yet for this object/period. */
    NOT_SYNCHRONIZED
}
