package com.example.marketing.insights.ai.client.exception;

public class AiInsightsUnavailableException
        extends AiInsightsClientException {

    public AiInsightsUnavailableException(String message) {
        super(message);
    }

    public AiInsightsUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}