package com.example.marketing.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaImageSyncResultDto {
    private int totalMetaImages;
    private int totalLocalVariants;
    private int newlyMatched;
    private int alreadyMatched;
    private int removedStale;
    private int unmatched;
}
