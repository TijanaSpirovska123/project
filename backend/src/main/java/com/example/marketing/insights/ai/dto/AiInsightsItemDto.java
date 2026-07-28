package com.example.marketing.insights.ai.dto;

import java.util.List;

public record AiInsightsItemDto(String title,
                                String explanation,
                                List<String> evidence,
                                String severity) {
}
