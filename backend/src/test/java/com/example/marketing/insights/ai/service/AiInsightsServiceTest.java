package com.example.marketing.insights.ai.service;



import com.example.marketing.insights.ai.client.AiInsightsClient;
import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.AiInsightsRequestDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AiInsightsServiceTest {

    private AiInsightsClient aiInsightsClient;
    private AiInsightsService aiInsightsService;

    @BeforeEach
    void setUp() {
        aiInsightsClient = mock(AiInsightsClient.class);
        aiInsightsService =
                new AiInsightsService(aiInsightsClient);
    }

    @Test
    void shouldSendContextAndQuestionToClient() {
        AnalysisContextDto analysisContext =
                AnalysisContextDto.builder().build();

        AiInsightResponseDto expectedResponse =
                createResponse();

        when(
                aiInsightsClient.analyze(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(expectedResponse);

        AiInsightResponseDto result =
                aiInsightsService.analyze(
                        analysisContext,
                        "  Explain the campaign performance.  "
                );

        ArgumentCaptor<AiInsightsRequestDto> captor =
                ArgumentCaptor.forClass(
                        AiInsightsRequestDto.class
                );

        verify(aiInsightsClient).analyze(
                captor.capture()
        );

        AiInsightsRequestDto sentRequest =
                captor.getValue();

        assertThat(result).isSameAs(expectedResponse);
        assertThat(sentRequest.analysisContext())
                .isSameAs(analysisContext);
        assertThat(sentRequest.question())
                .isEqualTo(
                        "Explain the campaign performance."
                );
    }

    @Test
    void shouldConvertBlankQuestionToNull() {
        AnalysisContextDto analysisContext =
                AnalysisContextDto.builder().build();

        when(
                aiInsightsClient.analyze(
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenReturn(createResponse());

        aiInsightsService.analyze(
                analysisContext,
                "   "
        );

        ArgumentCaptor<AiInsightsRequestDto> captor =
                ArgumentCaptor.forClass(
                        AiInsightsRequestDto.class
                );

        verify(aiInsightsClient).analyze(
                captor.capture()
        );

        assertThat(captor.getValue().question())
                .isNull();
    }

    @Test
    void shouldRejectNullAnalysisContext() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> aiInsightsService.analyze(
                                null,
                                "Analyze the campaign."
                        )
                )
                .withMessage(
                        "Analysis context must not be null."
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