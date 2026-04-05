package com.example.marketing.oauth.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdAccountConnectionDto {
    private Long id;
    private String provider;
    private String adAccountId;
    private String adAccountName;
    private String currency;
    private String timezoneName;
    private Boolean active;
    private LocalDateTime lastSynced;
    private LocalDateTime createdAt;
}
