package com.example.marketing.insights.ai.dto;

public record AiRecommendationItemDto(String action,
                                      String reason,
                                      String priority,
                                      String suggestedChange,
                                      String risk) {
}
