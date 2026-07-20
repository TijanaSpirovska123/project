package com.example.marketing.insights.mapper;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.strategy.MetaInsightsFetchStrategy;
import com.example.marketing.insights.util.InsightWarningCode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces (and verifies the fix for) the reported bug where synchronized campaign/ad-set/ad
 * insight snapshots always came back with {"metrics": [], "rawData": {"data": [...]}} even when
 * Meta's response genuinely contained data. Root cause: Meta's /insights response is always the
 * envelope {"data": [...rows...], "paging": {...}} — the real metric fields live inside each
 * element of "data", not at the top level. The old extractMetrics() scanned the top level of the
 * envelope (explicitly skipping "data"), so it could never find a metric, regardless of whether
 * "data" actually had rows.
 * <p>
 * Row extraction is delegated to the provider's own InsightsFetchStrategy.extractDataRows(),
 * so the mapper needs a working InsightsFetchStrategyRegistry — wired here with a real
 * MetaInsightsFetchStrategy (its unused HTTP-fetch dependencies are irrelevant to that method).
 */
class InsightsSnapshotMapperTest {

    private final InsightsSnapshotMapper mapper = Mappers.getMapper(InsightsSnapshotMapper.class);

    {
        InsightsFetchStrategyRegistry registry =
                new InsightsFetchStrategyRegistry(new MetaInsightsFetchStrategy(null, null));
        ReflectionTestUtils.setField(mapper, "strategyRegistry", registry);
    }

    // Wide default request range so tests that don't care about date matching never
    // spuriously trigger the dataPeriod-mismatch warning.
    private static final LocalDate DEFAULT_DATE_START = LocalDate.of(2000, 1, 1);
    private static final LocalDate DEFAULT_DATE_STOP = LocalDate.of(2100, 1, 1);

    private InsightSnapshotEntity entityWithRawJson(String rawJson) {
        return entityWithRawJson(rawJson, DEFAULT_DATE_START, DEFAULT_DATE_STOP);
    }

    private InsightSnapshotEntity entityWithRawJson(String rawJson, LocalDate dateStart, LocalDate dateStop) {
        return InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .rawJson(rawJson)
                .dateStart(dateStart)
                .dateStop(dateStop)
                .build();
    }

    private Optional<InsightMetricDto> metric(InsightSnapshotDto dto, String name) {
        return dto.getMetrics().stream().filter(m -> m.getName().equals(name)).findFirst();
    }

    @Test
    void nullRawJson_returnsEmptyMetricsWithSnapshotEmptyWarning() {
        InsightSnapshotEntity entity = entityWithRawJson(null);

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getMetrics()).isEmpty();
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_SNAPSHOT_EMPTY);
    }

    @Test
    void emptyProviderDataArray_returnsEmptyMetricsWithProviderResponseEmptyWarning() {
        // Exact shape of the reported bug symptom: {"metrics": [], "rawData": {"data": []}}
        InsightSnapshotEntity entity = entityWithRawJson("""
                {"data": [], "paging": {}}
                """);

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getMetrics()).isEmpty();
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_PROVIDER_RESPONSE_EMPTY);
    }

    @Test
    void missingDataField_returnsEmptyMetricsWithProviderResponseEmptyWarning() {
        InsightSnapshotEntity entity = entityWithRawJson("{\"paging\": {}}");

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getMetrics()).isEmpty();
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_PROVIDER_RESPONSE_EMPTY);
    }

    @Test
    void singleRowWithRealData_returnsNonEmptyCorrectMetrics_noWarnings() {
        // This is the case that was previously ALWAYS broken: real data present, metrics always empty.
        InsightSnapshotEntity entity = entityWithRawJson("""
                {
                  "data": [
                    {
                      "date_start": "2026-07-01",
                      "date_stop": "2026-07-01",
                      "campaign_id": "120228800815610082",
                      "campaign_name": "Sales campaign",
                      "spend": "123.45",
                      "impressions": "10000",
                      "clicks": "250",
                      "ctr": "2.5",
                      "actions": [
                        {"action_type": "purchase", "value": "4"},
                        {"action_type": "lead", "value": "2"}
                      ],
                      "action_values": [
                        {"action_type": "purchase", "value": "199.96"}
                      ]
                    }
                  ],
                  "paging": {}
                }
                """, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getWarnings()).isEmpty();
        assertThat(dto.getMetrics()).isNotEmpty();

        assertThat(metric(dto, "spend")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("123.45"));
        assertThat(metric(dto, "impressions")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("10000"));
        assertThat(metric(dto, "actions.purchase")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("4"));
        assertThat(metric(dto, "actions.lead")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("2"));
        assertThat(metric(dto, "action_values.purchase")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("199.96"));

        // Identifier/structural fields must not appear as metrics.
        assertThat(metric(dto, "campaign_id")).isEmpty();
        assertThat(metric(dto, "campaign_name")).isEmpty();
        assertThat(metric(dto, "date_start")).isEmpty();

        assertThat(dto.getActivityPeriod().getStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dto.getActivityPeriod().getStop()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void multiRowDailyData_sumsAdditiveMetrics_recalculatesRatiosFromTotals_excludesReach() {
        InsightSnapshotEntity entity = entityWithRawJson("""
                {
                  "data": [
                    {"date_start": "2026-07-01", "date_stop": "2026-07-01",
                     "spend": "100.00", "impressions": "1000", "clicks": "10",
                     "ctr": "1.0", "reach": "900"},
                    {"date_start": "2026-07-02", "date_stop": "2026-07-02",
                     "spend": "50.00", "impressions": "500", "clicks": "5",
                     "ctr": "1.0", "reach": "450"}
                  ],
                  "paging": {}
                }
                """, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getWarnings()).isEmpty();
        assertThat(metric(dto, "spend")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("150.00"));
        assertThat(metric(dto, "impressions")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("1500"));
        assertThat(metric(dto, "clicks")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("15"));

        // ctr is recalculated fresh from the summed totals (15/1500*100 = 1.00) — never averaged
        // from each day's own pre-computed ctr (which happens to agree here, but only by
        // coincidence since both days had the same 1.0 rate).
        assertThat(metric(dto, "ctr")).map(InsightMetricDto::getValueNumber)
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1.00"));

        // reach is fundamentally non-additive (can't sum unique users across days) — absent
        // whenever a snapshot spans more than one row.
        assertThat(metric(dto, "reach")).isEmpty();

        // dataPeriod spans the min/max across all rows, not just the last one seen.
        assertThat(dto.getActivityPeriod().getStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dto.getActivityPeriod().getStop()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void singleRow_normalizedActionMetrics_appearAlongsideRawPassThrough() {
        InsightSnapshotEntity entity = entityWithRawJson("""
                {
                  "data": [{
                    "date_start": "2026-07-01", "date_stop": "2026-07-01",
                    "spend": "50.00",
                    "actions": [
                      {"action_type": "omni_purchase", "value": "2"},
                      {"action_type": "offsite_conversion.fb_pixel_purchase", "value": "2"}
                    ],
                    "action_values": [{"action_type": "omni_purchase", "value": "80.00"}],
                    "purchase_roas": [{"action_type": "omni_purchase", "value": "1.6"}]
                  }],
                  "paging": {}
                }
                """, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        // Normalized names, not double-counted despite two overlapping purchase action types.
        assertThat(metric(dto, "purchases")).map(InsightMetricDto::getValueNumber)
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("2"));
        assertThat(metric(dto, "purchaseValue")).map(InsightMetricDto::getValueNumber)
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("80.00"));
        // roas is now always recalculated (purchaseValue/spend = 80.00/50.00), not passed
        // through from Meta's own purchase_roas value — they agree here (1.60) but aren't
        // required to going forward.
        assertThat(metric(dto, "roas")).map(InsightMetricDto::getValueNumber)
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1.60"));

        // Raw pass-through (per-action-type) entries are still preserved alongside the
        // normalized ones — normalization is additive, not a replacement.
        assertThat(metric(dto, "actions.omni_purchase")).isPresent();
        assertThat(metric(dto, "purchase_roas.omni_purchase")).isPresent();
    }

    @Test
    void multiRow_normalizedCounts_sum_normalizedRatios_excludedFromAggregate() {
        InsightSnapshotEntity entity = entityWithRawJson("""
                {
                  "data": [
                    {"date_start": "2026-07-01", "date_stop": "2026-07-01",
                     "actions": [{"action_type": "purchase", "value": "2"}],
                     "action_values": [{"action_type": "purchase", "value": "40.00"}],
                     "purchase_roas": [{"action_type": "purchase", "value": "2.0"}]},
                    {"date_start": "2026-07-02", "date_stop": "2026-07-02",
                     "actions": [{"action_type": "purchase", "value": "3"}],
                     "action_values": [{"action_type": "purchase", "value": "60.00"}],
                     "purchase_roas": [{"action_type": "purchase", "value": "3.0"}]}
                  ],
                  "paging": {}
                }
                """, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        // purchases/purchaseValue are additive counts/money — summed across days.
        assertThat(metric(dto, "purchases")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("5"));
        assertThat(metric(dto, "purchaseValue")).map(InsightMetricDto::getValueNumber)
                .contains(new BigDecimal("100.00"));

        // roas is a ratio — must not be summed/averaged across days (2.0 + 3.0 would be wrong).
        // It's still guaranteed present in the list (per the "always present" rule for
        // conversion metrics) but with a null value, since spend was never supplied here.
        assertThat(metric(dto, "roas")).map(InsightMetricDto::getValueNumber).isEmpty();
    }

    @Test
    void activityPeriod_exactlyMatchesRequestedRange_noWarning() {
        InsightSnapshotEntity entity = entityWithRawJson("""
                {"data": [{"date_start": "2026-06-16", "date_stop": "2026-07-16", "spend": "50.00"}], "paging": {}}
                """, LocalDate.of(2026, 6, 16), LocalDate.of(2026, 7, 16));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getWarnings()).isEmpty();
        assertThat(dto.getActivityPeriod().getStart()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(dto.getActivityPeriod().getStop()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    void activityPeriod_narrowerThanRequestedRange_noWarning_completeSyncDespiteSparseDelivery() {
        // Reproduces the exact previously-reported false positive: requesting 2026-06-16 to
        // 2026-07-16 but only having delivery through 2026-07-13 must NOT be flagged as an
        // incomplete/unsynchronized range — sparse delivery within a long requested range is
        // completely normal. syncComplete/syncStatus reflect whether the FETCH succeeded, not
        // whether the provider had delivery data for every requested day.
        InsightSnapshotEntity entity = entityWithRawJson("""
                {"data": [{"date_start": "2026-06-16", "date_stop": "2026-07-13", "spend": "50.00"}], "paging": {}}
                """, LocalDate.of(2026, 6, 16), LocalDate.of(2026, 7, 16));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getActivityPeriod().getStart()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(dto.getActivityPeriod().getStop()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(dto.getWarnings()).isEmpty();
        assertThat(dto.isSyncComplete()).isTrue();
        assertThat(dto.getSyncStatus()).isEqualTo(com.example.marketing.insights.util.InsightSyncStatus.COMPLETE);
    }

    @Test
    void activityPeriod_sparseDeliveryAcrossLongRequestedRange_matchesAcceptanceExample() {
        // The exact acceptance-criteria example: request 2026-01-01 to 2026-07-17, Meta only
        // delivers on 2026-02-03, 02-04, 02-18, 02-19 — must not produce
        // INSIGHT_DATE_RANGE_NOT_SYNCHRONIZED; syncComplete must be true; activityPeriod must be
        // 2026-02-03..2026-02-19; daysWithActivity must be 4.
        InsightSnapshotEntity entity = entityWithRawJson("""
                {"data": [
                    {"date_start": "2026-02-03", "date_stop": "2026-02-03", "spend": "10.00"},
                    {"date_start": "2026-02-04", "date_stop": "2026-02-04", "spend": "12.00"},
                    {"date_start": "2026-02-18", "date_stop": "2026-02-18", "spend": "8.00"},
                    {"date_start": "2026-02-19", "date_stop": "2026-02-19", "spend": "9.00"}
                 ], "paging": {}}
                """, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 17));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .doesNotContain(InsightWarningCode.INSIGHT_DATE_RANGE_NOT_SYNCHRONIZED);
        assertThat(dto.isSyncComplete()).isTrue();
        assertThat(dto.getSyncStatus()).isEqualTo(com.example.marketing.insights.util.InsightSyncStatus.COMPLETE);
        assertThat(dto.getActivityPeriod().getStart()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(dto.getActivityPeriod().getStop()).isEqualTo(LocalDate.of(2026, 2, 19));
        assertThat(dto.getDaysWithActivity()).isEqualTo(4);
    }

    @Test
    void activityPeriod_cannotBeDetermined_whenRowsLackDateFields() {
        InsightSnapshotEntity entity = entityWithRawJson("""
                {"data": [{"spend": "10.00"}], "paging": {}}
                """, LocalDate.of(2026, 6, 16), LocalDate.of(2026, 7, 16));

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        // Unknown, not wrongly flagged as incomplete — we simply can't tell from this data.
        assertThat(dto.getActivityPeriod()).isNull();
        assertThat(dto.getWarnings()).isEmpty();
    }

    @Test
    void noDeliveryAtAll_fullyProcessedFetch_flagsNoActivityWarning_notDateRangeNotSynchronized() {
        InsightSnapshotEntity entity = InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .rawJson("{\"data\": [], \"paging\": {}}")
                .dateStart(LocalDate.of(2026, 6, 16))
                .dateStop(LocalDate.of(2026, 7, 16))
                .paginationComplete(true)
                .build();

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getActivityPeriod()).isNull();
        assertThat(dto.getDaysWithActivity()).isEqualTo(0);
        assertThat(dto.isSyncComplete()).isTrue();
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_NO_ACTIVITY_IN_PERIOD);
    }

    @Test
    void paginationIncomplete_flagsPaginationIncompleteWarning_syncNotComplete() {
        InsightSnapshotEntity entity = InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .rawJson("""
                        {"data": [{"date_start": "2026-07-01", "date_stop": "2026-07-01", "spend": "10.00"}], "paging": {}}
                        """)
                .dateStart(LocalDate.of(2026, 7, 1))
                .dateStop(LocalDate.of(2026, 7, 1))
                .paginationComplete(false)
                .build();

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.isSyncComplete()).isFalse();
        assertThat(dto.getSyncStatus()).isEqualTo(com.example.marketing.insights.util.InsightSyncStatus.PARTIALLY_COMPLETE);
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_PAGINATION_INCOMPLETE);
    }

    @Test
    void malformedRawJson_returnsEmptyMetricsWithMetricsEmptyWarning() {
        InsightSnapshotEntity entity = entityWithRawJson("{not valid json");

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getMetrics()).isEmpty();
        assertThat(dto.getWarnings()).extracting(InsightWarningDto::getCode)
                .containsExactly(InsightWarningCode.INSIGHT_METRICS_EMPTY);
        // Falls back to the raw string rather than losing the data entirely.
        assertThat(dto.getRawData()).isEqualTo("{not valid json");
    }

    @Test
    void breakdownsJson_isMergedIntoRawDataUnderBreakdownsKey() {
        InsightSnapshotEntity entity = InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .rawJson("{\"data\": [{\"spend\": \"10.00\"}], \"paging\": {}}")
                .breakdownsJson("{\"age_gender\": [{\"age\": \"25-34\", \"spend\": \"10.00\"}]}")
                .build();

        InsightSnapshotDto dto = mapper.convertToBaseDto(entity);

        assertThat(dto.getWarnings()).isEmpty();
        assertThat(dto.getRawData().toString()).contains("breakdowns").contains("age_gender");
    }
}
