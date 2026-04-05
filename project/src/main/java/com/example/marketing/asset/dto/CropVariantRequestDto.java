package com.example.marketing.asset.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CropVariantRequestDto {
    private String variantKey;     // e.g. META_FEED_1080
    private int targetWidth;       // e.g. 1080
    private int targetHeight;      // e.g. 1080
    private CropRectDto crop;      // crop region in original image pixels
}
