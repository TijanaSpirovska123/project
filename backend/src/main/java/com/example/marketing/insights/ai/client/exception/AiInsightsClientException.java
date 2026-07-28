package com.example.marketing.insights.ai.client.exception;

public class AiInsightsClientException extends RuntimeException {

    public AiInsightsClientException(String message) {
        super(message);
    }

    public AiInsightsClientException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}