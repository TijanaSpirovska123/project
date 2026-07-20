package com.example.marketing.insights.api;

import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.service.InsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Regression coverage (requirement #6 — "response envelope") proving the existing
 * GET /api/insights/breakdown endpoint still wraps its result in the standard
 * {data, success, error} envelope after the breakdown calculation moved into
 * BreakdownAnalyticsService — the controller/endpoint itself was not touched by that move.
 */
@ExtendWith(MockitoExtension.class)
class InsightsControllerBreakdownEnvelopeTest {

    @Mock private InsightsService service;
    private InsightsController controller;

    @BeforeEach
    void setUp() {
        controller = new InsightsController(service);
    }

    @Test
    void breakdown_wrapsResultInStandardEnvelope() {
        List<InsightsBreakdownRowDto> rows = List.of(
                InsightsBreakdownRowDto.builder().dimension("country").dimensionValue("US").share(100.0).shareMetric("SPEND").build());
        when(service.breakdown(eq(1L), eq(Provider.META), eq("act_1"), eq("country"), any(), any(), isNull()))
                .thenReturn(rows);

        Authentication auth = new UsernamePasswordAuthenticationToken(new UserPrincipal(1L, "test"), null, List.of());

        var response = controller.breakdown(auth, Provider.META, "act_1", "country",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getError()).isNull();
        assertThat(response.getData()).isEqualTo(rows);
    }
}
