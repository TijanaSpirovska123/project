package com.example.marketing.insights.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightBatchSyncResultDto;
import com.example.marketing.insights.dto.InsightEntitySyncResultDto;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.strategy.MetaInsightsFetchStrategy;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers three of the explicitly required Phase-1 correctness scenarios for batch sync:
 * <ul>
 *   <li>an empty provider result (no delivery) must be reported as NO_ACTIVITY, never FAILED;</li>
 *   <li>a per-entity failure (missing from the provider response, or a persistence error) must
 *       not abort the rest of the batch, and must be counted separately from empty/successful;</li>
 *   <li>syncing the same request twice must update the existing row, never insert a duplicate
 *       (idempotency via the user/provider/adAccount/objectType/objectExternalId/dateStart/
 *       dateStop/timeIncrement unique key).</li>
 * </ul>
 * The provider fetch itself is faked (not mocked) by subclassing MetaInsightsFetchStrategy and
 * overriding only fetchForObjects — every other method (row extraction, action normalization,
 * query-param building) runs for real, so the metrics/warnings produced are the same ones a real
 * Meta response would produce.
 */
@ExtendWith(MockitoExtension.class)
class InsightsServiceBatchSyncTest {

    @Mock private InsightSnapshotRepository snapshotRepository;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserEntity user;

    private static class FakeMetaStrategy extends MetaInsightsFetchStrategy {
        private final Map<String, Map<String, Object>> canned;
        private final RuntimeException throwOnFetch;

        FakeMetaStrategy(Map<String, Map<String, Object>> canned) {
            super(null, null);
            this.canned = canned;
            this.throwOnFetch = null;
        }

        FakeMetaStrategy(RuntimeException throwOnFetch) {
            super(null, null);
            this.canned = null;
            this.throwOnFetch = throwOnFetch;
        }

        @Override
        public Map<String, Map<String, Object>> fetchForObjects(UserEntity user, Iterable<String> objectIds,
                Map<String, String> queryParams) {
            if (throwOnFetch != null) throw throwOnFetch;
            return canned;
        }
    }

    private InsightsService serviceWith(FakeMetaStrategy strategy) {
        InsightsFetchStrategyRegistry registry = new InsightsFetchStrategyRegistry(strategy);
        InsightsSnapshotMapper mapper = Mappers.getMapper(InsightsSnapshotMapper.class);
        ReflectionTestUtils.setField(mapper, "strategyRegistry", registry);
        return new InsightsService(snapshotRepository, userRepository, registry, mapper, objectMapper, null, null);
    }

    private InsightSyncRequestDto request(List<String> ids) {
        InsightSyncRequestDto req = new InsightSyncRequestDto();
        req.setProvider(Provider.META);
        req.setAdAccountId("act_123");
        req.setObjectType(InsightObjectType.CAMPAIGN);
        req.setObjectExternalIds(ids);
        req.setDateStart(LocalDate.of(2026, 7, 1));
        req.setDateStop(LocalDate.of(2026, 7, 1));
        return req;
    }

    private void stubUpsertAsInsertOnly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStopAndTimeIncrement(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            InsightSnapshotEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
    }

    @Test
    void emptyProviderResult_reportedAsNoActivity_notFailed() {
        Map<String, Object> emptyBody = Map.of("data", List.of(), "paging", Map.of());
        InsightsService service = serviceWith(new FakeMetaStrategy(Map.of("camp1", emptyBody)));
        stubUpsertAsInsertOnly();

        InsightBatchSyncResultDto result = service.syncBatchWithReport(1L, request(List.of("camp1")));

        assertThat(result.getRequestedCount()).isEqualTo(1);
        assertThat(result.getSuccessfulCount()).isEqualTo(0);
        assertThat(result.getEmptyResultCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getSyncStatus()).isEqualTo(InsightSyncStatus.COMPLETE);
        assertThat(result.getResults()).extracting(InsightEntitySyncResultDto::getStatus)
                .containsExactly("NO_ACTIVITY");
    }

    @Test
    void mixedBatch_someSuccessfulSomeEmptySomeMissing_reportsEachSeparately_partiallyComplete() {
        Map<String, Object> withData = Map.of(
                "data", List.of(Map.of("date_start", "2026-07-01", "date_stop", "2026-07-01", "spend", "10.00")),
                "paging", Map.of());
        Map<String, Object> emptyBody = Map.of("data", List.of(), "paging", Map.of());

        // camp3 is deliberately absent from the provider's response — an invalid ID/ownership
        // issue, not the same thing as an empty (but valid) result.
        InsightsService service = serviceWith(new FakeMetaStrategy(Map.of(
                "camp1", withData,
                "camp2", emptyBody)));
        stubUpsertAsInsertOnly();

        InsightBatchSyncResultDto result = service.syncBatchWithReport(1L, request(List.of("camp1", "camp2", "camp3")));

        assertThat(result.getRequestedCount()).isEqualTo(3);
        assertThat(result.getProcessedCount()).isEqualTo(3);
        assertThat(result.getSuccessfulCount()).isEqualTo(1);
        assertThat(result.getEmptyResultCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getSyncStatus()).isEqualTo(InsightSyncStatus.PARTIALLY_COMPLETE);

        Map<String, String> statusById = result.getResults().stream()
                .collect(java.util.stream.Collectors.toMap(InsightEntitySyncResultDto::getObjectExternalId,
                        InsightEntitySyncResultDto::getStatus));
        assertThat(statusById).containsEntry("camp1", "SYNCED");
        assertThat(statusById).containsEntry("camp2", "NO_ACTIVITY");
        assertThat(statusById).containsEntry("camp3", "FAILED");
    }

    @Test
    void totalProviderFetchFailure_marksEveryRequestedIdFailed_syncStatusFailed() {
        InsightsService service = serviceWith(new FakeMetaStrategy(new RuntimeException("provider unreachable")));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        InsightBatchSyncResultDto result = service.syncBatchWithReport(1L, request(List.of("camp1", "camp2")));

        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getSuccessfulCount()).isEqualTo(0);
        assertThat(result.getSyncStatus()).isEqualTo(InsightSyncStatus.FAILED);
    }

    @Test
    void repeatedSync_sameNaturalKey_updatesExistingRow_neverInsertsDuplicate() {
        Map<String, Object> withData = Map.of(
                "data", List.of(Map.of("date_start", "2026-07-01", "date_stop", "2026-07-01", "spend", "10.00")),
                "paging", Map.of());
        InsightsService service = serviceWith(new FakeMetaStrategy(Map.of("camp1", withData)));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AtomicLong idGen = new AtomicLong(1);
        List<InsightSnapshotEntity> saved = new ArrayList<>();
        // First call: nothing persisted yet. Subsequent calls: the repository "remembers" the
        // one row already saved under this natural key, exactly like a real unique-constrained
        // upsert lookup would.
        when(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStopAndTimeIncrement(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> saved.isEmpty() ? Optional.empty() : Optional.of(saved.get(0)));
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            InsightSnapshotEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(idGen.getAndIncrement());
            if (saved.isEmpty()) saved.add(e);
            return e;
        });

        service.syncBatchWithReport(1L, request(List.of("camp1")));
        service.syncBatchWithReport(1L, request(List.of("camp1")));
        service.syncBatchWithReport(1L, request(List.of("camp1")));

        // save() is invoked once per sync (3 total), but always against the SAME row — the
        // natural key never produces a second distinct entity/id.
        assertThat(saved).hasSize(1);
        assertThat(idGen.get()).isEqualTo(2); // only ever incremented once, on the very first insert
    }
}
