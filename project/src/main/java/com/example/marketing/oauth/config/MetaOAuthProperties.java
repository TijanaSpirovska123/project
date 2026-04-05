package com.example.marketing.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "meta")
@Component
@Data
public class MetaOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scopes;
    private String graphApiVersion;
    private String graphApiBaseUrl;
}
