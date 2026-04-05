package com.example.marketing.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "frontend")
@Component
@Data
public class FrontendProperties {
    private String successRedirectUrl;
    private String failureRedirectUrl;
    private String loginUrl;
}
