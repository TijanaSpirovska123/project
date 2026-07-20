package com.example.marketing.insights.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies breakdown() ROAS/share handling never fabricates a 0: a dimension bucket with real
 * purchase tracking gets a real recalculated ROAS, while a bucket with no purchase-related field
 * at all (not merely spend=0) gets roas=null and conversionDataAvailable=false — distinguishing
 * "no tracking data" from "tracked and genuinely zero". share is always expressed as a percentage
 * of total SPEND (shareMetric), never impressions/clicks.
 */
@ExtendWith(MockitoExtension.class)
class InsightsServiceBreakdownTest {

    @Mock private InsightSnapshotRepository snapshotRepository;
    @Mock private UserRepository userRepository;

    private InsightsService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        var breakdownAnalyticsService = new com.example.marketing.insights.analytics.service.BreakdownAnalyticsService(
                snapshotRepository, new ObjectMapper());
        service = new InsightsService(snapshotRepository, userRepository, null, null, new ObjectMapper(), null, breakdownAnalyticsService);
    }

    private void stubSnapshotsForCampaignsOnly(InsightSnapshotEntity snapshot) {
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.CAMPAIGN), any(), any()))
                .thenReturn(List.of(snapshot));
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.ADSET), any(), any()))
                .thenReturn(List.of());
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), eq(InsightObjectType.AD), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void breakdown_realTrackedRoas_vs_noConversionTracking_neverFabricatesZero() {
        String breakdownsJson = """
                {"country": [
                    {"country": "US", "spend": "10.00", "impressions": "1000", "clicks": "10",
                     "action_values": [{"action_type": "offsite_conversion.fb_pixel_purchase", "value": "50.00"}]},
                    {"country": "DE", "spend": "5.00", "impressions": "500", "clicks": "5"}
                ]}
                """;
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .breakdownsJson(breakdownsJson)
                .build();
        stubSnapshotsForCampaignsOnly(snap);

        List<InsightsBreakdownRowDto> rows = service.breakdown(1L, Provider.META, "act_1", "country",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), null);

        var us = rows.stream().filter(r -> r.getDimensionValue().equals("US")).findFirst().orElseThrow();
        var de = rows.stream().filter(r -> r.getDimensionValue().equals("DE")).findFirst().orElseThrow();

        assertThat(us.getConversionDataAvailable()).isTrue();
        assertThat(us.getRoas()).isEqualTo(5.0); // 50.00 / 10.00

        assertThat(de.getConversionDataAvailable()).isFalse();
        assertThat(de.getRoas()).isNull(); // no purchase tracking data at all — not a fabricated 0

        assertThat(us.getShareMetric()).isEqualTo("SPEND");
        assertThat(de.getShareMetric()).isEqualTo("SPEND");
        // share is spend-share: US 10/(10+5) = 66.67%, DE 5/15 = 33.33%
        assertThat(us.getShare()).isEqualTo(66.67);
        assertThat(de.getShare()).isEqualTo(33.33);
    }

    @Test
    void breakdown_zeroSpendBucket_ctrAndShareNull_neverFabricatedZero() {
        String breakdownsJson = """
                {"country": [
                    {"country": "FR", "spend": "0.00", "impressions": "0", "clicks": "0"}
                ]}
                """;
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .breakdownsJson(breakdownsJson)
                .build();
        stubSnapshotsForCampaignsOnly(snap);

        List<InsightsBreakdownRowDto> rows = service.breakdown(1L, Provider.META, "act_1", "country",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), null);

        var fr = rows.stream().filter(r -> r.getDimensionValue().equals("FR")).findFirst().orElseThrow();
        assertThat(fr.getCtr()).isNull();
        assertThat(fr.getShare()).isNull();
        assertThat(fr.getConversionDataAvailable()).isFalse();
        assertThat(fr.getRoas()).isNull();
    }
}
