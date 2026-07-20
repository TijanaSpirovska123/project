package com.example.marketing.insights.util;

/** Why a metric's value is unavailable (null) rather than a real zero. */
public final class InsightUnavailableReason {

    private InsightUnavailableReason() {}

    /** This provider/object level doesn't support the metric at all. */
    public static final String NOT_SUPPORTED = "NOT_SUPPORTED";

    /** The provider's response simply didn't include this field/action type. */
    public static final String NOT_RETURNED_BY_PROVIDER = "NOT_RETURNED_BY_PROVIDER";

    /** No conversion/purchase tracking event of this type was ever reported — can't tell if
     * that's because tracking isn't configured or genuinely zero events occurred. */
    public static final String CONVERSION_TRACKING_UNAVAILABLE = "CONVERSION_TRACKING_UNAVAILABLE";

    /** Purchases were tracked, but no purchase-value action was reported alongside them. */
    public static final String PURCHASE_VALUE_UNAVAILABLE = "PURCHASE_VALUE_UNAVAILABLE";

    /** The formula's denominator was zero or absent. */
    public static final String DIVISION_BY_ZERO = "DIVISION_BY_ZERO";

    /** Reserved for when campaign objective is known and doesn't support this metric (needs campaign-context join — not yet implemented). */
    public static final String INCOMPATIBLE_OBJECTIVE = "INCOMPATIBLE_OBJECTIVE";

    /** Reserved for cases needing more data than is currently available to decide (not yet implemented). */
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
}
