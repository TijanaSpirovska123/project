package com.example.marketing.page.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PageStrategyRegistry {
    private final Map<Provider, PageStrategy> byPlatform;

    public PageStrategyRegistry(List<PageStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(PageStrategy::platform, s -> s));
    }

    public PageStrategy of(Provider platform) {
        return Objects.requireNonNull(byPlatform.get(platform),
                () -> "No PageStrategy for " + platform);
    }
}
