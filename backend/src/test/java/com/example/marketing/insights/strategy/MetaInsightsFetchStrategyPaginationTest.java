package com.example.marketing.insights.strategy;

import com.example.marketing.infrastructure.strategy.PlatformClient;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.strategy.InsightsFetchStrategy.ProviderFetchResult;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Meta insights pagination-following (paging.cursors.after): must continue fetching
 * pages until exhausted, merge all rows into one combined result, and distinguish a first-page
 * failure (total failure — throws, since nothing was ever fetched) from a later-page failure
 * (partial success — keeps rows already fetched, marks paginationComplete=false rather than
 * silently discarding them or claiming full success).
 */
class MetaInsightsFetchStrategyPaginationTest {

    private final UserEntity user = new UserEntity();

    private MetaInsightsFetchStrategy strategyWith(IntFunction<ResponseEntity<Map>> pages) {
        PlatformClient fakeClient = new PlatformClient() {
            private int calls = 0;

            @Override public Provider provider() { return Provider.META; }

            @Override public ResponseEntity<Map> postForm(String path, MultiValueMap<String, String> form, String token) {
                throw new UnsupportedOperationException();
            }

            @Override public ResponseEntity<Map> postMultipart(String path, MultiValueMap<String, Object> formData, String token) {
                throw new UnsupportedOperationException();
            }

            @Override public ResponseEntity<Map> get(String path, Map<String, String> queryParams, String token) {
                return pages.apply(calls++);
            }

            @Override public ResponseEntity<Map> delete(String objectId, Map<String, String> payload, String token) {
                throw new UnsupportedOperationException();
            }
        };

        PlatformClientRegistry registry = new PlatformClientRegistry(List.of(fakeClient));
        registry.init();

        TokenService fakeTokenService = new TokenService(null) {
            @Override public String getAccessToken(UserEntity user, Provider provider) {
                return "test-token";
            }
        };

        return new MetaInsightsFetchStrategy(registry, fakeTokenService);
    }

    private static ResponseEntity<Map> pageWithRows(int rowCount, String afterCursor) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) rows.add(Map.of("spend", "1.00"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", rows);
        body.put("paging", afterCursor == null
                ? Map.of()
                : Map.of("cursors", Map.of("after", afterCursor)));
        return ResponseEntity.ok(body);
    }

    @Test
    void multiPageResponse_followsCursorUntilExhausted_mergesAllRows_markedComplete() {
        MetaInsightsFetchStrategy strategy = strategyWith(call -> switch (call) {
            case 0 -> pageWithRows(2, "cursor1");
            case 1 -> pageWithRows(3, "cursor2");
            case 2 -> pageWithRows(1, null);
            default -> throw new IllegalStateException("unexpected extra call " + call);
        });

        ProviderFetchResult result = strategy.fetchForObject(user, "camp1", Map.of());

        assertThat(result.paginationComplete()).isTrue();
        assertThat((List<?>) result.body().get("data")).hasSize(6);
    }

    @Test
    void laterPageFails_keepsRowsFromPriorPages_marksIncomplete() {
        MetaInsightsFetchStrategy strategy = strategyWith(call -> {
            if (call == 0) return pageWithRows(2, "cursor1");
            throw new RuntimeException("simulated transient failure on page " + (call + 1));
        });

        ProviderFetchResult result = strategy.fetchForObject(user, "camp1", Map.of());

        assertThat(result.paginationComplete()).isFalse();
        assertThat((List<?>) result.body().get("data")).hasSize(2);
    }

    @Test
    void firstPageFails_throwsImmediately_noPartialResultReturned() {
        MetaInsightsFetchStrategy strategy = strategyWith(call -> {
            throw new RuntimeException("simulated total failure");
        });

        assertThatThrownBy(() -> strategy.fetchForObject(user, "camp1", Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void singlePageResponse_noCursor_completesInOnePage() {
        MetaInsightsFetchStrategy strategy = strategyWith(call -> pageWithRows(4, null));

        ProviderFetchResult result = strategy.fetchForAccount(user, "act_1", Map.of());

        assertThat(result.paginationComplete()).isTrue();
        assertThat((List<?>) result.body().get("data")).hasSize(4);
    }

    @Test
    void cursorLoopNeverTerminating_stopsAtSafetyCap_marksIncomplete() {
        // A misbehaving/malicious provider could return a cursor forever; this must never hang.
        MetaInsightsFetchStrategy strategy = strategyWith(call -> pageWithRows(1, "same-cursor-forever"));

        ProviderFetchResult result = strategy.fetchForObject(user, "camp1", Map.of());

        assertThat(result.paginationComplete()).isFalse();
        assertThat((List<?>) result.body().get("data")).hasSizeGreaterThan(0);
    }
}
