package com.example.marketing.infrastructure.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public abstract class BasePlatformDto {
    private Long id;
    private String name;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** e.g. "META" (pick one and stick to it) */
    private String platform;

    private Long userId;

    /** act_123... shared by Campaign/AdSet/Ad */
    private String adAccountId;

    /** platform object id (campaign/adset/ad id from Meta) */
    private String externalId;
}
