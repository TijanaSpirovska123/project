package com.example.marketing.adset.strategy;


import com.example.marketing.campaign.strategy.CampaignStrategy;
import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdSetStrategyRegistry {
    private final Map<Provider, AdSetStrategy> byPlatform;

    public AdSetStrategyRegistry(List<AdSetStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(AdSetStrategy::platform, s -> s));
    }

    public AdSetStrategy of(Provider platform) {
        AdSetStrategy strategy = byPlatform.get(platform);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for platform: " + platform);
        }
        return strategy;
    }

}
