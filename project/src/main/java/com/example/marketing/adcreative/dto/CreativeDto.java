package com.example.marketing.adcreative.dto;

import com.example.marketing.infrastructure.dto.BasePlatformDto;
import lombok.Data;

@Data
public class CreativeDto extends BasePlatformDto {

    // Use BasePlatformDto.name (REMOVE local 'name')
    // Use BasePlatformDto.adAccountId (REMOVE local 'adAccountId')
    // Use BasePlatformDto.externalId as the creative platform id (REMOVE creativeId)

    // Mode 1: existing post
    private String objectStoryId; // "{pageId}_{postId}"

    // Mode 2: uploaded asset link creative
    private String pageId;
    private String objectUrl;
    private String imageHash;
    private String message;
    private String headline;

    // optional
    private String urlTags;
    private String objectType;
}
