package com.example.marketing.pixel.strategy;

import com.example.marketing.infrastructure.util.Provider;

import java.util.Map;

public interface PixelStrategy {
    Provider platform();

    // /act_{adAccountId}/adspixels
    String listPixelsPath(String adAccountId);
    Map<String, String> listPixelsQuery();
}
