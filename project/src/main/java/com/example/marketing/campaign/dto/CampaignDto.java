package com.example.marketing.campaign.dto;

import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.infrastructure.dto.BasePlatformDto;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CampaignDto extends BasePlatformDto {
    // Raw data from Meta API — not persisted, populated on-demand from Redis/API
    private Map<String, Object> rawData;

    // Nested for when fetching with children
    private List<AdSetDto> adSets;
}
