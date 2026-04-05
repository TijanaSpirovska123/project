package com.example.marketing.asset.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StoredAssetDto {
    private Long id;
    private Long userId;

    private String assetType;
    private String originalFilename;
    private String mimeType;
    private Long sizeBytes;
    private String hash;
    private String status;
    private List<String> tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<StoredAssetVariantDto> variants;
}
