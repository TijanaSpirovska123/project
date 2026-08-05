package com.example.marketing.pixel.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PixelStrategyRegistry {
    private final Map<Provider, PixelStrategy> byPlatform;

    public PixelStrategyRegistry(List<PixelStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(PixelStrategy::platform, s -> s));
    }

    public PixelStrategy of(Provider platform) {
        return Objects.requireNonNull(byPlatform.get(platform),
                () -> "No PixelStrategy for " + platform);
    }
}
