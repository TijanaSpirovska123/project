package com.example.marketing.insights.util;

/**
 * Warning codes surfaced in insight responses when data is missing, incomplete, or could not
 * be interpreted — so callers can distinguish "genuinely zero" from "we don't actually know."
 */
public final class InsightWarningCode {

    private InsightWarningCode() {}

    /** No snapshot at all exists for the requested object/date range (nothing has been synced). */
    public static final String INSIGHT_DATA_NOT_FOUND = "INSIGHT_DATA_NOT_FOUND";

    /** A snapshot row exists but has no raw provider data stored on it. */
    public static final String INSIGHT_SNAPSHOT_EMPTY = "INSIGHT_SNAPSHOT_EMPTY";

    /** Raw provider data exists but no metrics could be extracted from it. */
    public static final String INSIGHT_METRICS_EMPTY = "INSIGHT_METRICS_EMPTY";

    /**
     * The requested date range was NOT fully fetched/processed — real evidence of incomplete
     * synchronization (a page failed, a batch entity failed, part of the range was never even
     * requested). NOT used merely because the provider had no delivery data for part of the
     * range — see INSIGHT_NO_ACTIVITY_IN_PERIOD for that (expected, not an error) case.
     */
    public static final String INSIGHT_DATE_RANGE_NOT_SYNCHRONIZED = "INSIGHT_DATE_RANGE_NOT_SYNCHRONIZED";

    /** The provider (Meta) returned a response with no data rows for this snapshot, for an unknown/unconfirmed reason. */
    public static final String INSIGHT_PROVIDER_RESPONSE_EMPTY = "INSIGHT_PROVIDER_RESPONSE_EMPTY";

    /**
     * The request was fully and successfully processed, but the provider reported zero
     * delivery for the entire requested period — not an error, not incomplete sync.
     */
    public static final String INSIGHT_NO_ACTIVITY_IN_PERIOD = "INSIGHT_NO_ACTIVITY_IN_PERIOD";

    /** Some, but not all, of a sync request succeeded (e.g. one page or one batch entity failed). */
    public static final String INSIGHT_SYNC_PARTIALLY_COMPLETE = "INSIGHT_SYNC_PARTIALLY_COMPLETE";

    /** The sync request failed entirely. */
    public static final String INSIGHT_SYNC_FAILED = "INSIGHT_SYNC_FAILED";

    /** Not all pages of a paginated provider response could be fetched. */
    public static final String INSIGHT_PAGINATION_INCOMPLETE = "INSIGHT_PAGINATION_INCOMPLETE";

    /** Provider data was fetched successfully but could not be fully persisted. */
    public static final String INSIGHT_PERSISTENCE_PARTIAL = "INSIGHT_PERSISTENCE_PARTIAL";
}
