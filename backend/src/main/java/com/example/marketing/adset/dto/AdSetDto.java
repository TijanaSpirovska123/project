package com.example.marketing.adset.dto;

import com.example.marketing.ad.dto.AdDto;
import com.example.marketing.infrastructure.dto.BasePlatformDto;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdSetDto extends BasePlatformDto {
    private Long campaignId;             // local DB id
    private String campaignExternalId;   // platform campaign id (Meta campaign "id")

    // Raw data from Meta API — not persisted, populated on-demand from Redis/API
    private Map<String, Object> rawData;

    private List<AdDto> ads;
}
