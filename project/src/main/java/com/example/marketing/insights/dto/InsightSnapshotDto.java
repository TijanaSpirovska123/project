package com.example.marketing.insights.dto;

import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.infrastructure.util.Provider;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InsightSnapshotDto {
    private Long id;
    private Provider provider;
    private String adAccountId;

    private InsightObjectType objectType;
    private String objectExternalId;

    private LocalDate dateStart;
    private LocalDate dateStop;
    private Integer timeIncrement;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InsightMetricDto> metrics;
    private Object rawData; 

}