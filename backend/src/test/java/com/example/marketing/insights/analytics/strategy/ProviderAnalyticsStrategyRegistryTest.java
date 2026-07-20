package com.example.marketing.insights.analytics.strategy;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.util.InsightObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 24 "Strategy architecture" (55-59): correct provider selection via the registry, no
 * switch/if-provider checks, unsupported-provider rejection, duplicate-provider rejection, and
 * that a second/test provider strategy can be added without touching any generic service.
 */
class ProviderAnalyticsStrategyRegistryTest {

    private static class FakeTikTokStrategy implements ProviderAnalyticsStrategy {
        @Override public Provider getProvider() { return Provider.TIKTOK; }
        @Override public ProviderAnalyticsCapabilitiesDto getCapabilities() {
            return ProviderAnalyticsCapabilitiesDto.builder().provider(Provider.TIKTOK).build();
        }
        @Override public Set<CanonicalMetric> getSupportedMetrics() { return Set.of(CanonicalMetric.SPEND); }
        @Override public Set<BreakdownDimension> getSupportedBreakdowns() { return Set.of(); }
        @Override public Set<InsightObjectType> getSupportedObjectTypes() { return Set.of(InsightObjectType.CAMPAIGN); }
        @Override public boolean supportsReachAggregation(AnalyticsScope scope) { return false; }
        @Override public boolean supportsMetric(CanonicalMetric metric, InsightObjectType objectType) { return false; }
        @Override public Optional<String> validateAnalyticsRequest(AnalyticsFilterRequest request) { return Optional.empty(); }
        @Override public List<DeterministicFindingDto> createProviderSpecificFindings(AnalysisContextDto context) { return List.of(); }
    }

    @Test
    void metaStrategy_selectedFromRegistry() {
        MetaAnalyticsStrategy meta = new MetaAnalyticsStrategy();
        ProviderAnalyticsStrategyRegistry registry = new ProviderAnalyticsStrategyRegistry(List.of(meta));

        assertThat(registry.getStrategy(Provider.META)).isSameAs(meta);
        assertThat(registry.isSupported(Provider.META)).isTrue();
    }

    @Test
    void unsupportedProvider_throwsClearException() {
        ProviderAnalyticsStrategyRegistry registry = new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));

        assertThat(registry.isSupported(Provider.GOOGLE)).isFalse();
        assertThatThrownBy(() -> registry.getStrategy(Provider.GOOGLE))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void duplicateProviderStrategy_rejectedAtConstruction() {
        assertThatThrownBy(() -> new ProviderAnalyticsStrategyRegistry(
                List.of(new MetaAnalyticsStrategy(), new MetaAnalyticsStrategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void newProviderStrategy_addableWithoutModifyingRegistry() {
        // Proves a second provider can be registered purely by adding a bean — no switch/if in
        // the registry itself needed any change to support TikTok.
        ProviderAnalyticsStrategyRegistry registry = new ProviderAnalyticsStrategyRegistry(
                List.of(new MetaAnalyticsStrategy(), new FakeTikTokStrategy()));

        assertThat(registry.isSupported(Provider.META)).isTrue();
        assertThat(registry.isSupported(Provider.TIKTOK)).isTrue();
        assertThat(registry.getStrategy(Provider.TIKTOK)).isInstanceOf(FakeTikTokStrategy.class);
    }
}
