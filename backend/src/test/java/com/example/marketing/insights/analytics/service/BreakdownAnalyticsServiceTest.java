package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression coverage (Step 13 / requirement #6) for the extracted breakdown-by-dimension logic
 * — response structure, sorting by share descending, 2-decimal rounding, spend-share
 * calculation, explicit shareMetric, null (not fabricated-zero) ROAS, and conversionDataAvailable
 * — proving the extraction into BreakdownAnalyticsService changed nothing observable about the
 * existing GET /api/insights/breakdown endpoint's behavior.
 */
@ExtendWith(MockitoExtension.class)
class BreakdownAnalyticsServiceTest {

    @Mock private InsightSnapshotRepository snapshotRepository;

    private BreakdownAnalyticsService service;
    private final UserEntity user = new UserEntity();

    @BeforeEach
    void setUp() {
        // Constructed here (not as a field initializer) since @Mock fields aren't populated
        // until after instance construction but before @BeforeEach runs.
        service = new BreakdownAnalyticsService(snapshotRepository, new ObjectMapper());
    }

    private InsightSnapshotEntity snapshotWithCountryBreakdown(String breakdownsJson) {
        return InsightSnapshotEntity.builder().provider(Provider.META).breakdownsJson(breakdownsJson).build();
    }

    private void stubCampaignsOnly(InsightSnapshotEntity snap) {
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.CAMPAIGN), any(), any())).thenReturn(List.of(snap));
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.ADSET), any(), any())).thenReturn(List.of());
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.AD), any(), any())).thenReturn(List.of());
    }

    @Test
    void responseStructure_containsAllExpectedFields() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10",
                    "action_values": [{"action_type": "offsite_conversion.fb_pixel_purchase", "value": "50.00"}]}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows).hasSize(1);
        InsightsBreakdownRowDto row = rows.get(0);
        assertThat(row.getDimension()).isEqualTo("country");
        assertThat(row.getDimensionValue()).isEqualTo("US");
        assertThat(row.getSpend()).isEqualTo(10.00);
        assertThat(row.getImpressions()).isEqualTo(1000);
        assertThat(row.getClicks()).isEqualTo(10);
        assertThat(row.getShareMetric()).isEqualTo("SPEND");
    }

    @Test
    void sortedByShareDescending() {
        String json = """
                {"country": [
                    {"country": "US", "spend": "70.00", "impressions": "1000", "clicks": "10"},
                    {"country": "DE", "spend": "30.00", "impressions": "500", "clicks": "5"}
                ]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows).extracting(InsightsBreakdownRowDto::getDimensionValue).containsExactly("US", "DE");
        assertThat(rows.get(0).getShare()).isEqualTo(70.0);
        assertThat(rows.get(1).getShare()).isEqualTo(30.0);
    }

    @Test
    void rounding_alwaysTwoDecimalPlaces() {
        String json = """
                {"country": [{"country": "US", "spend": "33.333", "impressions": "3", "clicks": "1"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        // 1/3 * 100 = 33.333... -> rounded to 33.33
        assertThat(rows.get(0).getCtr()).isEqualTo(33.33);
    }

    @Test
    void spendShare_zeroTotalSpend_isNull_notFabricatedZero() {
        String json = """
                {"country": [{"country": "US", "spend": "0.00", "impressions": "0", "clicks": "0"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getShare()).isNull();
        assertThat(rows.get(0).getCtr()).isNull();
    }

    @Test
    void roas_null_whenNoConversionTrackingAtAll_conversionDataAvailableFalse() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getRoas()).isNull();
        assertThat(rows.get(0).getConversionDataAvailable()).isFalse();
    }

    @Test
    void roas_realValue_whenConversionTrackingPresent() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10",
                    "action_values": [{"action_type": "offsite_conversion.fb_pixel_purchase", "value": "50.00"}]}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getRoas()).isEqualTo(5.0);
        assertThat(rows.get(0).getConversionDataAvailable()).isTrue();
    }

    @Test
    void reach_null_whenProviderNeverReturnedIt() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getReach()).isNull();
    }

    @Test
    void reach_realZero_staysZero_notNull() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10", "reach": "0"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getReach()).isZero();
    }

    @Test
    void reach_realValue_whenProviderReturnsIt() {
        String json = """
                {"country": [{"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10", "reach": "820"}]}
                """;
        stubCampaignsOnly(snapshotWithCountryBreakdown(json));

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows.get(0).getReach()).isEqualTo(820L);
    }

    @Test
    void noSnapshots_returnsEmptyList_notNull() {
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        List<InsightsBreakdownRowDto> rows = service.breakdown(user, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(rows).isNotNull().isEmpty();
    }

    @Test
    void safeRatio_doubleOverload_zeroDenominator_returnsNull() {
        assertThat(BreakdownAnalyticsService.safeRatio(10.0, 0.0, 100.0)).isNull();
    }

    @Test
    void safeRatio_doubleOverload_validInputs_computesCorrectly() {
        assertThat(BreakdownAnalyticsService.safeRatio(50.0, 5000.0, 100.0)).isEqualTo(1.0);
    }
}
