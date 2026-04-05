// src/main/java/com/example/marketing/adcreative/strategy/CreativeStrategyRegistry.java
package com.example.marketing.adcreative.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class CreativeStrategyRegistry {
    private final Map<Provider, CreativeStrategy> byPlatform;

    public CreativeStrategyRegistry(List<CreativeStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(CreativeStrategy::platform, s -> s));
    }

    public CreativeStrategy of(Provider provider) {
        return Objects.requireNonNull(byPlatform.get(provider),
                () -> "No CreativeStrategy for " + provider);
    }
}
