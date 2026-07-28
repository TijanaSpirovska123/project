package com.example.marketing.insights.ai.service;

import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.GenerateAiInsightRequestDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.service.AnalysisContextBuilder;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiInsightGenerationService {

    private final AnalysisContextBuilder analysisContextBuilder;
    private final AiInsightsService aiInsightsService;

    public AiInsightResponseDto generate(
            UserEntity user,
            GenerateAiInsightRequestDto request
    ) {
        AnalysisContextDto analysisContext =
                analysisContextBuilder.build(
                        user,
                        request.contextRequest()
                );

        return aiInsightsService.analyze(
                analysisContext,
                request.question()
        );
    }
}
