package com.example.marketing.asset.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class CropVariantsRequestDto {
    private List<CropVariantRequestDto> variants;
}
