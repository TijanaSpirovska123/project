package com.example.marketing.insights.ai.client;

import com.example.marketing.insights.ai.client.exception.AiInsightsClientException;
import com.example.marketing.insights.ai.client.exception.AiInsightsInvalidResponseException;
import com.example.marketing.insights.ai.client.exception.AiInsightsRequestRejectedException;
import com.example.marketing.insights.ai.client.exception.AiInsightsUnavailableException;
import com.example.marketing.insights.ai.config.AiInsightsProperties;
import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.AiInsightsRequestDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AiInsightsClient {

    private final RestClient restClient;
    private final AiInsightsProperties properties;

    public AiInsightsClient(
            @Qualifier("aiInsightsRestClient")
            RestClient restClient,
            AiInsightsProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public AiInsightResponseDto analyze(
            AiInsightsRequestDto request
    ) {
        if (!properties.enabled()) {
            throw new AiInsightsUnavailableException(
                    "AI insights integration is disabled."
            );
        }

        try {
            AiInsightResponseDto response = restClient
                    .post()
                    .uri(properties.analyzePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()

                    // Invalid AnalysisContext sent by Spring Boot.
                    .onStatus(
                            status -> status.value()
                                    == HttpStatus.UNPROCESSABLE_ENTITY.value(),
                            (httpRequest, httpResponse) -> {
                                throw new AiInsightsRequestRejectedException(
                                        buildErrorMessage(
                                                "FastAPI rejected the request",
                                                httpResponse
                                        )
                                );
                            }
                    )

                    // Gemini returned invalid structured output.
                    .onStatus(
                            status -> status.value()
                                    == HttpStatus.BAD_GATEWAY.value(),
                            (httpRequest, httpResponse) -> {
                                throw new AiInsightsInvalidResponseException(
                                        buildErrorMessage(
                                                "FastAPI received an invalid LLM response",
                                                httpResponse
                                        )
                                );
                            }
                    )

                    // Gemini or FastAPI temporarily unavailable.
                    .onStatus(
                            status -> status.value()
                                    == HttpStatus.SERVICE_UNAVAILABLE.value(),
                            (httpRequest, httpResponse) -> {
                                throw new AiInsightsUnavailableException(
                                        buildErrorMessage(
                                                "AI insights service is unavailable",
                                                httpResponse
                                        )
                                );
                            }
                    )

                    // Any other Python-side server error.
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (httpRequest, httpResponse) -> {
                                throw new AiInsightsUnavailableException(
                                        buildErrorMessage(
                                                "AI insights service failed",
                                                httpResponse
                                        )
                                );
                            }
                    )

                    .body(AiInsightResponseDto.class);

            if (response == null) {
                throw new AiInsightsInvalidResponseException(
                        "FastAPI returned an empty response."
                );
            }

            return response;

        } catch (AiInsightsClientException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            throw new AiInsightsUnavailableException(
                    "Could not connect to the AI insights service.",
                    exception
            );

        } catch (RestClientException exception) {
            throw new AiInsightsClientException(
                    "Unexpected error while calling the AI insights service.",
                    exception
            );
        }
    }

    private String buildErrorMessage(
            String message,
            ClientHttpResponse response
    ) throws IOException {
        String responseBody = StreamUtils.copyToString(
                response.getBody(),
                StandardCharsets.UTF_8
        );

        if (responseBody.isBlank()) {
            return message
                    + ". HTTP status: "
                    + response.getStatusCode().value();
        }

        return message + ": " + responseBody;
    }
}