package com.example.marketing.ad.dto;

import com.example.marketing.infrastructure.dto.BasePlatformDto;
import lombok.Data;

import java.util.Map;

@Data
public class AdDto extends BasePlatformDto {
    private Long adSetId;            // local FK
    private String adSetExternalId;  // platform adset id (Meta "adset_id")
    private String adSetName;        // denormalized for table display

    private String creativeId;       // Meta creative_id (e.g. "120210012345678")

    // Raw data from Meta API — not persisted, populated on-demand from Redis/API
    private Map<String, Object> rawData;
}
