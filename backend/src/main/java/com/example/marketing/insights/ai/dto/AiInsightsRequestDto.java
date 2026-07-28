package com.example.marketing.insights.ai.dto;

import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiInsightsRequestDto (

        @NotNull
        @Valid
        AnalysisContextDto analysisContext,

        @Size(min = 3, max = 1000)
        String question
) {
}
