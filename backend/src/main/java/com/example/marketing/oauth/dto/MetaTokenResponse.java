package com.example.marketing.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn
) {}
