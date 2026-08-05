package com.example.marketing.pixel.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.example.marketing.infrastructure.util.MetaForm.normalizeAct;

@Component
public class MetaPixelStrategy implements PixelStrategy {

    @Override public Provider platform() { return Provider.META; }

    @Override public String listPixelsPath(String adAccountId) { return normalizeAct(adAccountId) + "/adspixels"; }

    @Override public Map<String, String> listPixelsQuery() {
        return Map.of("fields", "id,name");
    }
}
