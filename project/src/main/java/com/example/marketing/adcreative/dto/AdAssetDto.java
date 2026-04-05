package com.example.marketing.adcreative.dto;



import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdAssetDto {
    private Long id;
    private String imageHash;
    private String url;
}
