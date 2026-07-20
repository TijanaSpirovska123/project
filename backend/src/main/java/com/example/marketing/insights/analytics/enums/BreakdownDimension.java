package com.example.marketing.insights.analytics.enums;

/**
 * Canonical breakdown dimensions. Maps to the lowercase dimension keys already used by
 * {@code InsightsService}/{@code InsightsBreakdownRowDto} (see {@link #wireName()}) so the
 * existing /api/insights/breakdown endpoint and its stored breakdownsJson format are reused
 * as-is rather than duplicated.
 */
public enum BreakdownDimension {
    AGE("age"),
    GENDER("gender"),
    COUNTRY("country"),
    PLACEMENT("placement"),
    DEVICE("device"),
    PLATFORM("publisher_platform");

    private final String wireName;

    BreakdownDimension(String wireName) {
        this.wireName = wireName;
    }

    /** The lowercase dimension key already used in query params / InsightsBreakdownRowDto.dimension. */
    public String wireName() {
        return wireName;
    }

    public static BreakdownDimension fromWireName(String value) {
        if (value == null) return null;
        for (BreakdownDimension d : values()) {
            if (d.wireName.equalsIgnoreCase(value) || d.name().equalsIgnoreCase(value)) return d;
        }
        return null;
    }
}
