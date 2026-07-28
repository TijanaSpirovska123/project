package com.example.marketing.insights.ai.client;

import com.example.marketing.insights.ai.client.exception.AiInsightsUnavailableException;
import com.example.marketing.insights.ai.config.AiInsightsProperties;
import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.AiInsightsRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;

class AiInsightsClientTest {

    private MockRestServiceServer mockServer;
    private AiInsightsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ai-test");

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        RestClient restClient = builder.build();

        AiInsightsProperties properties =
                new AiInsightsProperties(
                        true,
                        "http://ai-test",
                        "/insights/analyze",
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(30)
                );

        client = new AiInsightsClient(
                restClient,
                properties
        );
    }

    @Test
    void shouldReturnAiInsightsResponse() {
        mockServer.expect(
                        requestTo(
                                "http://ai-test/insights/analyze"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "summary": "Campaign summary",
                                  "answer": "Campaign answer",
                                  "keyInsights": [],
                                  "risks": [],
                                  "recommendations": [],
                                  "limitations": [],
                                  "conclusion": "Campaign conclusion",
                                  "confidenceScore": 0.70
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        AiInsightsRequestDto request =
                new AiInsightsRequestDto(
                        null,
                        "Explain the campaign performance."
                );

        AiInsightResponseDto response =
                client.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.summary())
                .isEqualTo("Campaign summary");
        assertThat(response.confidenceScore())
                .isEqualByComparingTo("0.70");

        mockServer.verify();
    }

    @Test
    void shouldMapServiceUnavailableResponse() {
        mockServer.expect(
                        requestTo(
                                "http://ai-test/insights/analyze"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withStatus(
                                HttpStatus.SERVICE_UNAVAILABLE
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(
                                        """
                                        {
                                          "detail":
                                          "Gemini is temporarily unavailable."
                                        }
                                        """
                                )
                );

        AiInsightsRequestDto request =
                new AiInsightsRequestDto(
                        null,
                        "Explain the campaign performance."
                );

        assertThatThrownBy(
                () -> client.analyze(request)
        )
                .isInstanceOf(
                        AiInsightsUnavailableException.class
                )
                .hasMessageContaining(
                        "Gemini is temporarily unavailable"
                );

        mockServer.verify();
    }
}