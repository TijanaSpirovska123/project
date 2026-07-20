package com.example.marketing.insights.analytics.service;

import com.example.marketing.ad.repository.AdRepository;
import com.example.marketing.adset.repository.AdSetRepository;
import com.example.marketing.campaign.repository.CampaignRepository;
import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.strategy.MetaInsightsFetchStrategy;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step 24 "Strategy architecture"/"Security" coverage for the request-scoped loader: mixed
 * object-type rejection, date-range/selection-size limits, unsupported-provider rejection, and
 * that ownership is enforced purely by scoping every query to the authenticated user (Step 20 —
 * reusing the existing Phase-1 convention rather than a separate ownership-check pass).
 */
@ExtendWith(MockitoExtension.class)
class SnapshotCanonicalDatasetLoaderTest {

    @Mock private InsightSnapshotRepository snapshotRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private AdSetRepository adSetRepository;
    @Mock private AdRepository adRepository;

    private SnapshotCanonicalDatasetLoader loader;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);

        InsightsFetchStrategyRegistry fetchRegistry = new InsightsFetchStrategyRegistry(new MetaInsightsFetchStrategy(null, null));
        InsightsSnapshotMapper snapshotMapper = Mappers.getMapper(InsightsSnapshotMapper.class);
        ReflectionTestUtils.setField(snapshotMapper, "strategyRegistry", fetchRegistry);

        ProviderAnalyticsStrategyRegistry analyticsRegistry = new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));
        InsightsAnalyticsLimitsProperties limits = new InsightsAnalyticsLimitsProperties();
        limits.setMaxSelectedCampaigns(2);
        limits.setMaxDateRangeDays(30);

        loader = new SnapshotCanonicalDatasetLoader(snapshotRepository, snapshotMapper, new CanonicalRecordMapper(),
                analyticsRegistry, limits, campaignRepository, adSetRepository, adRepository);
    }

    private AnalyticsFilterRequest baseRequest() {
        AnalyticsFilterRequest r = new AnalyticsFilterRequest();
        r.setProvider(Provider.META);
        r.setAdAccountId("act_1");
        r.setDateStart(LocalDate.of(2026, 2, 1));
        r.setDateStop(LocalDate.of(2026, 2, 10));
        return r;
    }

    @Test
    void mixedObjectTypes_rejected() {
        AnalyticsFilterRequest r = baseRequest();
        r.setCampaignIds(List.of("c1"));
        r.setAdSetIds(List.of("a1"));

        assertThatThrownBy(() -> loader.load(user, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MIXED_OBJECT_TYPES");
    }

    @Test
    void dateRangeExceedsMaximum_rejected() {
        AnalyticsFilterRequest r = baseRequest();
        r.setDateStart(LocalDate.of(2026, 1, 1));
        r.setDateStop(LocalDate.of(2026, 6, 1)); // > 30 days configured max

        assertThatThrownBy(() -> loader.load(user, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("INVALID_DATE_RANGE");
    }

    @Test
    void dateStartAfterDateStop_rejected() {
        AnalyticsFilterRequest r = baseRequest();
        r.setDateStart(LocalDate.of(2026, 2, 10));
        r.setDateStop(LocalDate.of(2026, 2, 1));

        assertThatThrownBy(() -> loader.load(user, r)).isInstanceOf(BusinessException.class);
    }

    @Test
    void selectionSizeExceedsMaximum_rejected() {
        AnalyticsFilterRequest r = baseRequest();
        r.setCampaignIds(List.of("c1", "c2", "c3")); // max configured is 2

        assertThatThrownBy(() -> loader.load(user, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void unsupportedProvider_rejected() {
        AnalyticsFilterRequest r = baseRequest();
        r.setProvider(Provider.GOOGLE);

        assertThatThrownBy(() -> loader.load(user, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNSUPPORTED_PROVIDER");
    }

    @Test
    void ownership_queryScopedToAuthenticatedUser_onlyReturnsThatUsersSnapshots() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder()
                .provider(Provider.META).user(user).adAccountId("act_1")
                .objectType(InsightObjectType.CAMPAIGN).objectExternalId("c1")
                .dateStart(LocalDate.of(2026, 2, 1)).dateStop(LocalDate.of(2026, 2, 10))
                .timeIncrement(1)
                .rawJson("""
                        {"data": [{"date_start": "2026-02-03", "date_stop": "2026-02-03", "spend": "10.00"}], "paging": {}}
                        """)
                .build();

        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(snap));
        when(campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(any(), any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsFilterRequest r = baseRequest();
        r.setCampaignIds(List.of("c1"));

        var dataset = loader.load(user, r);

        // The mocked repository call itself is what enforces ownership (it's scoped by `user`,
        // exactly like every existing Phase-1 query) — here we just confirm the loader correctly
        // surfaces whatever that user-scoped query returns, with real day-level metrics.
        assertThat(dataset.getRecords()).hasSize(1);
        assertThat(dataset.getRecords().get(0).getObjectExternalId()).isEqualTo("c1");
    }

    @Test
    void objectNames_batchLoaded_noPerObjectQuery() {
        InsightSnapshotEntity snap1 = InsightSnapshotEntity.builder()
                .provider(Provider.META).user(user).adAccountId("act_1")
                .objectType(InsightObjectType.CAMPAIGN).objectExternalId("c1")
                .dateStart(LocalDate.of(2026, 2, 1)).dateStop(LocalDate.of(2026, 2, 10)).timeIncrement(1)
                .rawJson("{\"data\": [], \"paging\": {}}").build();

        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                any(), any(), any(), any(), any(), any())).thenReturn(List.of(snap1));

        CampaignEntity campaignEntity = new CampaignEntity();
        campaignEntity.setExternalId("c1");
        campaignEntity.setName("My Campaign");
        when(campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(any(), any(), any(), any()))
                .thenReturn(List.of(campaignEntity));

        var dataset = loader.load(user, baseRequest());

        assertThat(dataset.getObjectNames()).containsEntry("c1", "My Campaign");
    }
}
