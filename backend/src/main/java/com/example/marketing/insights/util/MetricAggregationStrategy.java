package com.example.marketing.insights.util;

/**
 * How a metric behaves when combining multiple stored rows (e.g. several daily snapshots)
 * into one result.
 */
public enum MetricAggregationStrategy {
    /** Additive — sum across rows (spend, impressions, clicks, purchases, ...). */
    SUM,
    /** A ratio that must be recalculated from summed totals, never summed/averaged itself
     * (ctr, cpc, cpm, frequency, roas, costPerPurchase, ...). */
    RECALCULATED_RATIO,
    /** Cannot be correctly combined across overlapping rows at all (reach — summing daily
     * reach double-counts users seen on more than one day). Only trustworthy for a single row. */
    NON_ADDITIVE,
    /** Use the most recent row's value rather than summing (reserved for future metrics). */
    LATEST_VALUE,
    /** Aggregation is provider-defined and doesn't fit the other categories (reserved). */
    PROVIDER_SPECIFIC
}
