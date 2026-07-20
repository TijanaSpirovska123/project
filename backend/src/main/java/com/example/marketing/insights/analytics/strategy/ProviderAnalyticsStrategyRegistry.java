package com.example.marketing.insights.analytics.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps each {@link Provider} to exactly one {@link ProviderAnalyticsStrategy} bean. Adding a new
 * provider (e.g. TikTok) means implementing the interface and registering it as a Spring bean —
 * nothing here or in any shared analytics service needs to change. Never a switch/if-provider
 * check: callers always go through {@link #getStrategy(Provider)}.
 */
@Component
public class ProviderAnalyticsStrategyRegistry {

    private final Map<Provider, ProviderAnalyticsStrategy> strategies;

    public ProviderAnalyticsStrategyRegistry(List<ProviderAnalyticsStrategy> strategyBeans) {
        Map<Provider, ProviderAnalyticsStrategy> map = new java.util.EnumMap<>(Provider.class);
        for (ProviderAnalyticsStrategy strategy : strategyBeans) {
            ProviderAnalyticsStrategy existing = map.put(strategy.getProvider(), strategy);
            if (existing != null) {
                throw new IllegalStateException("Duplicate ProviderAnalyticsStrategy for provider "
                        + strategy.getProvider() + ": " + existing.getClass().getSimpleName()
                        + " and " + strategy.getClass().getSimpleName());
            }
        }
        this.strategies = Map.copyOf(map);
    }

    public ProviderAnalyticsStrategy getStrategy(Provider provider) {
        ProviderAnalyticsStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new UnsupportedOperationException("No analytics strategy registered for provider: " + provider);
        }
        return strategy;
    }

    public boolean isSupported(Provider provider) {
        return strategies.containsKey(provider);
    }
}
