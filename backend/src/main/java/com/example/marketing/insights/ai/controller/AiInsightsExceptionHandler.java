package com.example.marketing.insights.ai.controller;

import com.example.marketing.insights.ai.client.exception.AiInsightsClientException;
import com.example.marketing.insights.ai.client.exception.AiInsightsInvalidResponseException;
import com.example.marketing.insights.ai.client.exception.AiInsightsRequestRejectedException;
import com.example.marketing.insights.ai.client.exception.AiInsightsUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiInsightsExceptionHandler {

    @ExceptionHandler(AiInsightsRequestRejectedException.class)
    public ResponseEntity<AiErrorResponse> handleRejectedRequest(
            AiInsightsRequestRejectedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new AiErrorResponse(
                        "AI_REQUEST_REJECTED",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AiInsightsInvalidResponseException.class)
    public ResponseEntity<AiErrorResponse> handleInvalidResponse(
            AiInsightsInvalidResponseException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new AiErrorResponse(
                        "AI_INVALID_RESPONSE",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AiInsightsUnavailableException.class)
    public ResponseEntity<AiErrorResponse> handleUnavailable(
            AiInsightsUnavailableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AiErrorResponse(
                        "AI_SERVICE_UNAVAILABLE",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AiInsightsClientException.class)
    public ResponseEntity<AiErrorResponse> handleClientError(
            AiInsightsClientException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new AiErrorResponse(
                        "AI_SERVICE_ERROR",
                        exception.getMessage()
                ));
    }

    public record AiErrorResponse(
            String code,
            String message
    ) {
    }
}
