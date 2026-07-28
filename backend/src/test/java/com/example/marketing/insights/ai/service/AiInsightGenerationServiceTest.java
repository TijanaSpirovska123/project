package com.example.marketing.insights.ai.service;

import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.GenerateAiInsightRequestDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextRequestDto;
import com.example.marketing.insights.analytics.service.AnalysisContextBuilder;
import com.example.marketing.user.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInsightGenerationServiceTest {

    private AnalysisContextBuilder analysisContextBuilder;
    private AiInsightsService aiInsightsService;
    private AiInsightGenerationService generationService;

    @BeforeEach
    void setUp() {
        analysisContextBuilder =
                mock(AnalysisContextBuilder.class);

        aiInsightsService =
                mock(AiInsightsService.class);

        generationService =
                new AiInsightGenerationService(
                        analysisContextBuilder,
                        aiInsightsService
                );
    }

    @Test
    void shouldBuildContextAndGenerateAiInsights() {
        UserEntity user = mock(UserEntity.class);

        AnalysisContextRequestDto contextRequest =
                mock(AnalysisContextRequestDto.class);

        AnalysisContextDto analysisContext =
                AnalysisContextDto.builder().build();

        AiInsightResponseDto expectedResponse =
                createResponse();

        when(
                analysisContextBuilder.build(
                        user,
                        contextRequest
                )
        ).thenReturn(analysisContext);

        when(
                aiInsightsService.analyze(
                        analysisContext,
                        "Explain the campaign performance."
                )
        ).thenReturn(expectedResponse);

        GenerateAiInsightRequestDto request =
                new GenerateAiInsightRequestDto(
                        contextRequest,
                        "Explain the campaign performance."
                );

        AiInsightResponseDto result =
                generationService.generate(
                        user,
                        request
                );

        assertThat(result).isSameAs(expectedResponse);

        verify(analysisContextBuilder).build(
                user,
                contextRequest
        );

        verify(aiInsightsService).analyze(
                analysisContext,
                "Explain the campaign performance."
        );
    }

    private AiInsightResponseDto createResponse() {
        return new AiInsightResponseDto(
                "Summary",
                "Answer",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Conclusion",
                new BigDecimal("0.70")
        );
    }
}
