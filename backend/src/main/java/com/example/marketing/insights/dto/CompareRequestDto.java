package com.example.marketing.insights.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.util.InsightObjectType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CompareRequestDto {
    private List<Provider> platforms;
    private InsightObjectType entityType;
    private String adAccountId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
