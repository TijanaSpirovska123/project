package com.example.marketing.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaAdAccount(
        String id,
        String name,
        @JsonProperty("account_status") Integer accountStatus,
        String currency,
        @JsonProperty("timezone_name") String timezoneName,
        MetaBusiness business
) {}
