package com.example.marketing.insights.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiInsightResponseDto(String summary,
                                   String answer,
                                   List<AiInsightsItemDto> keyInsights,
                                   List<AiInsightsItemDto> risks,
                                   List<AiRecommendationItemDto> recommendations,
                                   List<String> limitations,
                                   String conclusion,
                                   BigDecimal confidenceScore) {
}
