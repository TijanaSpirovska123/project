package com.example.marketing.ad.strategy;


import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class AdStrategyRegistry {
    private final Map<Provider, AdStrategy> byPlatform;

    public AdStrategyRegistry(List<AdStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(AdStrategy::platform, s -> s));
    }

    public AdStrategy of(Provider platform) {
        return Objects.requireNonNull(byPlatform.get(platform),
                () -> "No AdStrategy for " + platform);
    }
}

