package com.example.marketing.asset.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoredAssetVariantDto {
    private Long id;
    private String variantKey;

    private String bucket;
    private String objectKey;

    private Integer width;
    private Integer height;

    private String metaImageHash;
    private String metaVideoId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
