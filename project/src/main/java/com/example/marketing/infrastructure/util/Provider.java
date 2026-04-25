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

    /**
     * Maps a Spring Security OAuth2 client registration ID (e.g. "facebook", "google")
     * to the canonical Provider used in the database.
     * Add new entries here when wiring up additional OAuth2 registrations.
     */
    public static Provider fromRegistrationId(String registrationId) {
        if (registrationId == null) throw new IllegalArgumentException("registrationId is null");
        return switch (registrationId.trim().toLowerCase()) {
            case "facebook"  -> META;
            case "google"    -> GOOGLE;
            case "tiktok"    -> TIKTOK;
            case "linkedin"  -> LINKEDIN;
            case "instagram" -> INSTAGRAM;
            case "x", "twitter" -> X;
            default -> throw new IllegalArgumentException(
                    "No Provider mapping for OAuth2 registration: " + registrationId);
        };
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