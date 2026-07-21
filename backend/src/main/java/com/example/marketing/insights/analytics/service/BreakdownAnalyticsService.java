package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Breakdown-by-dimension analytics (Step 13), extracted verbatim (same math, same response
 * shape) from the original {@code InsightsService.breakdown()} so the existing
 * GET /api/insights/breakdown endpoint keeps behaving identically while the calculation itself
 * lives in one dedicated, reusable service. {@code InsightsService.breakdown()} now delegates
 * here.
 */
@Service
@RequiredArgsConstructor
public class BreakdownAnalyticsService {

    private final InsightSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /** Maps each breakdown dimension to the group key used in breakdownsJson (mirrors InsightsService.DIMENSION_TO_GROUP for the read path only). */
    private static final Map<String, String> DIMENSION_TO_GROUP = Map.ofEntries(
            Map.entry("age",                "age_gender"),
            Map.entry("gender",             "age_gender"),
            Map.entry("country",            "country"),
            Map.entry("impression_device",  "placement"),
            Map.entry("publisher_platform", "placement"),
            Map.entry("placement",          "placement"),
            Map.entry("device",             "placement"),
            Map.entry("os",                 "placement")
    );

    public List<InsightsBreakdownRowDto> breakdown(UserEntity user, Provider provider, String adAccountId,
            String dimension, LocalDate dateStart, LocalDate dateStop, List<String> campaignIds) {
        List<InsightSnapshotEntity> snapshots = fetchSnapshots(user, provider, adAccountId, dateStart, dateStop, campaignIds);
        return computeBreakdown(snapshots, dimension);
    }

    private List<InsightSnapshotEntity> fetchSnapshots(UserEntity user, Provider provider, String adAccountId,
            LocalDate dateStart, LocalDate dateStop, List<String> campaignIds) {
        List<InsightSnapshotEntity> snapshots = new ArrayList<>();
        if (campaignIds != null && !campaignIds.isEmpty()) {
            for (String campaignId : campaignIds) {
                snapshots.addAll(snapshotRepository
                        .findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                                user, provider, adAccountId, InsightObjectType.CAMPAIGN,
                                campaignId, dateStart, dateStop));
            }
        } else {
            for (InsightObjectType type : List.of(InsightObjectType.CAMPAIGN, InsightObjectType.ADSET, InsightObjectType.AD)) {
                snapshots.addAll(snapshotRepository
                        .findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                                user, provider, adAccountId, type, dateStart, dateStop));
            }
        }
        return snapshots;
    }

    /**
     * Pure computation over already-fetched snapshots — never queries the repository itself. Used
     * both by {@link #breakdown} (which fetches its own snapshots, preserving the existing
     * GET /api/insights/breakdown behavior exactly) and by AnalysisContextBuilder, which instead
     * passes {@code CanonicalDataset.getSnapshotEntities()} — the SAME snapshots already fetched
     * once by SnapshotCanonicalDatasetLoader — so computing a breakdown for the unified context
     * never triggers a second database query.
     */
    public List<InsightsBreakdownRowDto> computeBreakdown(List<InsightSnapshotEntity> snapshots, String dimension) {
        Map<String, double[]> aggMap = new LinkedHashMap<>();
        Map<String, Boolean> conversionDataAvailableByBucket = new LinkedHashMap<>();

        for (InsightSnapshotEntity snap : snapshots) {
            List<Object> rows = resolveBreakdownRows(snap, dimension);
            for (Object item : rows) {
                if (!(item instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;

                Object dimValue = extractDimensionValue(row, dimension);
                if (dimValue == null) continue;

                String dimStr = String.valueOf(dimValue);
                double[] agg = aggMap.computeIfAbsent(dimStr, k -> new double[5]);
                agg[0] += toDouble(row.get("spend"));
                agg[1] += toDouble(row.get("impressions"));
                agg[2] += toDouble(row.get("clicks"));
                agg[3] += toDouble(row.get("reach"));

                Double revenue = extractRoasRevenue(row);
                if (revenue != null) {
                    agg[4] += revenue;
                    conversionDataAvailableByBucket.put(dimStr, true);
                } else {
                    conversionDataAvailableByBucket.putIfAbsent(dimStr, false);
                }
            }
        }

        if (aggMap.isEmpty()) return List.of();

        double totalSpend = aggMap.values().stream().mapToDouble(a -> a[0]).sum();

        return aggMap.entrySet().stream()
                .map(e -> {
                    double[] a = e.getValue();
                    boolean conversionDataAvailable = Boolean.TRUE.equals(conversionDataAvailableByBucket.get(e.getKey()));
                    Double ctr = safeRatio(a[2], a[1], 100);
                    Double roas = conversionDataAvailable ? safeRatio(a[4], a[0], 1) : null;
                    Double share = safeRatio(a[0], totalSpend, 100);
                    return InsightsBreakdownRowDto.builder()
                            .dimension(dimension)
                            .dimensionValue(e.getKey())
                            .spend(round2(a[0]))
                            .impressions((long) a[1])
                            .clicks((long) a[2])
                            .reach((long) a[3])
                            .ctr(round2(ctr))
                            .roas(round2(roas))
                            .conversionDataAvailable(conversionDataAvailable)
                            .share(round2(share))
                            .shareMetric("SPEND")
                            .build();
                })
                .sorted(Comparator.comparing(InsightsBreakdownRowDto::getShare,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Object> resolveBreakdownRows(InsightSnapshotEntity snap, String dimension) {
        if (snap.getBreakdownsJson() != null) {
            try {
                Map<String, Object> bj = objectMapper.readValue(snap.getBreakdownsJson(), Map.class);
                String groupKey = DIMENSION_TO_GROUP.get(dimension);
                if (groupKey != null && bj.containsKey(groupKey)) {
                    Object rows = bj.get(groupKey);
                    if (rows instanceof List) return (List<Object>) rows;
                }
            } catch (Exception ignored) {}
        }

        if (snap.getRawJson() != null) {
            try {
                Map<String, Object> raw = objectMapper.readValue(snap.getRawJson(), Map.class);
                List<Object> dataList = (List<Object>) raw.get("data");
                return dataList != null ? dataList : List.of();
            } catch (Exception ignored) {}
        }

        return List.of();
    }

    private Object extractDimensionValue(Map<String, Object> row, String dimension) {
        return switch (dimension) {
            case "placement" -> row.get("publisher_platform");
            case "device"    -> row.get("impression_device");
            case "os"        -> classifyOs((String) row.get("impression_device"));
            case "dow"       -> dayOfWeekFromDate((String) row.get("date_start"));
            default          -> row.get(dimension);
        };
    }

    private static String classifyOs(String impressionDevice) {
        if (impressionDevice == null || impressionDevice.isBlank()) return null;
        String d = impressionDevice.toLowerCase(Locale.ROOT);
        if (d.contains("iphone") || d.contains("ipad") || d.contains("ipod") || d.contains("ios")) return "iOS";
        if (d.contains("android")) return "Android";
        if (d.contains("desktop")) return "Desktop";
        return "Other";
    }

    private static String dayOfWeekFromDate(String dateStart) {
        if (dateStart == null || dateStart.isBlank()) return null;
        try {
            return LocalDate.parse(dateStart).getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        } catch (Exception e) {
            return null;
        }
    }

    static double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }

    /** numerator/denominator (× multiplier); null if the denominator is zero — undefined, not a fabricated 0. */
    static Double safeRatio(double numerator, double denominator, double multiplier) {
        return denominator == 0 ? null : (numerator / denominator) * multiplier;
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static Double round2(Double val) {
        return val == null ? null : Math.round(val * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private Double extractRoasRevenue(Map<String, Object> row) {
        Object purchaseRoas = row.get("purchase_roas");
        if (purchaseRoas instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                Double v = toNullableDouble(m.get("value"));
                if (v != null) return v;
            }
        }
        Object actionValues = row.get("action_values");
        if (actionValues instanceof List<?> avList) {
            for (Object av : avList) {
                if (av instanceof Map<?, ?> m
                        && "offsite_conversion.fb_pixel_purchase".equals(m.get("action_type"))) {
                    return toNullableDouble(m.get("value"));
                }
            }
        }
        return null;
    }

    private static Double toNullableDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }
}
