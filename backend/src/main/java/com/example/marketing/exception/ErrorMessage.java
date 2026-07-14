package com.example.marketing.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorMessage {

    private String code;
    private String description;
    private String error;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private LocalDateTime timestamp;

    // Optional fields for additional context
    private String path;
    private String method;

    /**
     * Create error message with code, description and error details
     */
    public static ErrorMessage of(String code, String description, String error) {
        return ErrorMessage.builder()
                .code(code)
                .description(description)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create error message with code and description only
     */
    public static ErrorMessage of(String code, String description) {
        return of(code, description, null);
    }

    /**
     * Create error message with request context
     */
    public static ErrorMessage withContext(String code, String description, String error,
                                          String path, String method) {
        return ErrorMessage.builder()
                .code(code)
                .description(description)
                .error(error)
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Deprecated
    @Getter
    @Setter
    public static class ErrorResponse {

        private String code;
        private String description;
        private String error;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
        private LocalDateTime timestamp;

        public ErrorResponse(String code, String description, String error) {
            this.code = code;
            this.description = description;
            this.error = error;
            this.timestamp = LocalDateTime.now();
        }

//        public ErrorResponse(String code, String description) {
//            this(code, description, null);
//        }
    }
}
