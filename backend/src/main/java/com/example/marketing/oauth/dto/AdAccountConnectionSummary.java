package com.example.marketing.oauth.dto;

import java.time.LocalDateTime;

public record AdAccountConnectionSummary(
        String provider,
        boolean connected,
        LocalDateTime lastSynced,
        String tokenStatus
) {}
