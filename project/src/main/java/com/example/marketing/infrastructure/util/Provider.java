package com.example.marketing.infrastructure.util;

import lombok.Getter;

@Getter
public enum Provider {
    FACEBOOK("Facebook"),
    META("Meta"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    GOOGLE("Google"),
    LINKEDIN("LinkedIn"),
    X("X");

    private final String platform;

    Provider(String platform) {
        this.platform = platform;
    }

    public static Provider from(String value) {
        if (value == null) throw new IllegalArgumentException("Provider is null");
        return Provider.valueOf(value.trim().toUpperCase());
    }


    public record PlatformContext(Provider platform, String accessToken, String adAccountId, Long userId) { }


    public static Provider getProviderEnum(String platform) {
        for (Provider provider : Provider.values()) {
            if (provider.getPlatform().equalsIgnoreCase(platform)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Wrong Provider platform provided: " + platform);
    }
}