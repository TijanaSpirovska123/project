package com.example.marketing.asset.dto;

import lombok.Data;

import java.util.List;

@Data
public class SetTagsRequestDto {
    private List<String> tags;
}
