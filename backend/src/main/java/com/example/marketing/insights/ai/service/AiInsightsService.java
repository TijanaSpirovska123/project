package com.example.marketing.insights.ai.service;

import com.example.marketing.insights.ai.client.AiInsightsClient;
import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.AiInsightsRequestDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AiInsightsService {

    private final AiInsightsClient aiInsightsClient;

    public AiInsightsService(
            AiInsightsClient aiInsightsClient
    ) {
        this.aiInsightsClient = aiInsightsClient;
    }

    public AiInsightResponseDto analyze(
            AnalysisContextDto analysisContext,
            String question
    ) {
        Objects.requireNonNull(
                analysisContext,
                "Analysis context must not be null."
        );

        String normalizedQuestion = normalizeQuestion(question);

        AiInsightsRequestDto request =
                new AiInsightsRequestDto(
                        analysisContext,
                        normalizedQuestion
                );

        return aiInsightsClient.analyze(request);
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        return question.trim();
    }
}