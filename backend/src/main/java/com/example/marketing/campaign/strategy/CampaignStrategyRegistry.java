package com.example.marketing.campaign.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class CampaignStrategyRegistry {
    private final Map<Provider, CampaignStrategy> byPlatform;

    public CampaignStrategyRegistry(List<CampaignStrategy> strategies) {
        this.byPlatform = strategies.stream()
                .collect(Collectors.toMap(CampaignStrategy::platform, s -> s));
    }

    public CampaignStrategy of(Provider platform) {
        return Objects.requireNonNull(byPlatform.get(platform),
                () -> "No CampaignStrategy for " + platform);
    }
}
