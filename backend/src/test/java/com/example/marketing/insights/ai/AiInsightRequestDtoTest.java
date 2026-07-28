package com.example.marketing.insights.ai;

import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class AiInsightRequestDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseCamelCaseForAiFields() throws Exception {
        String json = objectMapper.writeValueAsString(
                new AiInsightResponseDto(
                        "Summary",
                        "Answer",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        "Conclusion",
                        new java.math.BigDecimal("0.70")
                )
        );

        assertThat(json).contains("\"keyInsights\"");
        assertThat(json).contains("\"confidenceScore\"");
        assertThat(json).doesNotContain("key_insights");
        assertThat(json).doesNotContain("confidence_score");
    }
}