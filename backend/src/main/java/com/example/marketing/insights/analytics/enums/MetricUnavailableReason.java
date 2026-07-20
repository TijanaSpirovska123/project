package com.example.marketing.insights.analytics.enums;

/**
 * Canonical, typed superset of {@link com.example.marketing.insights.util.InsightUnavailableReason}
 * (which stays as-is for Phase-1 DTO backward compatibility). Analytics services use this enum
 * internally and serialize its {@code name()} — the same string values as the Phase-1 constants
 * for the reasons both layers share.
 */
public enum MetricUnavailableReason {
    NOT_SUPPORTED,
    NOT_RETURNED_BY_PROVIDER,
    CONVERSION_TRACKING_UNAVAILABLE,
    PURCHASE_VALUE_UNAVAILABLE,
    DIVISION_BY_ZERO,
    MIXED_CURRENCY,
    NON_ADDITIVE_AGGREGATION,
    INSUFFICIENT_DATA,
    PARTIAL_SYNC
}
