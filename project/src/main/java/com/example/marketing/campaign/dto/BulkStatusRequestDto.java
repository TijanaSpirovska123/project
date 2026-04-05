package com.example.marketing.campaign.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkStatusRequestDto {
    private List<Long> ids;
    private String status;
}
