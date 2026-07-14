package com.example.marketing.insights.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InsightsFetchStrategyRegistry {

    private final Map<Provider, InsightsFetchStrategy> strategies;

    // In the future: add TikTokInsightsFetchStrategy, GoogleInsightsFetchStrategy
    // etc.
    // Spring will inject all InsightsFetchStrategy beans — but we need provider
    // binding.
    // Simple approach: each strategy exposes its provider.
    public InsightsFetchStrategyRegistry(MetaInsightsFetchStrategy meta) {
        this.strategies = Map.of(Provider.META, meta);
    }

    public InsightsFetchStrategy of(Provider provider) {
        InsightsFetchStrategy s = strategies.get(provider);
        if (s == null)
            throw new UnsupportedOperationException("No insights strategy for provider: " + provider);
        return s;
    }
}