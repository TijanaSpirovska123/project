package com.example.marketing.infrastructure.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlatformClientRegistry {

    // All PlatformClient implementations (MetaPlatformClient, GooglePlatformClient, etc.)
    private final List<PlatformClient> clientList;

    private Map<Provider, PlatformClient> clients = new HashMap<>();

    public PlatformClientRegistry(List<PlatformClient> clientList) {
        this.clientList = clientList;
    }

    @PostConstruct
    public void init() {
        for (PlatformClient client : clientList) {
            Provider provider = client.provider();
            if (clients.containsKey(provider)) {
                throw new IllegalStateException("Duplicate PlatformClient for " + provider);
            }
            clients.put(provider, client);
        }
    }

    public PlatformClient of(Provider provider) {
        PlatformClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No PlatformClient found for provider: " + provider);
        }
        return client;
    }
}