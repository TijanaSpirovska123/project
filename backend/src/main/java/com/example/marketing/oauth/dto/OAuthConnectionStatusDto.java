package com.example.marketing.oauth.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OAuthConnectionStatusDto {
    private String provider;
    private boolean connected;
    private LocalDateTime tokenExpiry;
    private String externalUserId;
    private List<String> grantedScopes;
}

