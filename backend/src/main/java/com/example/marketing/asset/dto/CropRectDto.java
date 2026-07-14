package com.example.marketing.asset.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CropRectDto {
    private int x; // px in ORIGINAL
    private int y;
    private int w;
    private int h;
}
