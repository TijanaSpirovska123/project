package com.example.marketing.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final StatusErrorResponse status;
    private final String detail;

    private BusinessException(StatusErrorResponse status, String detail, Throwable cause) {
        super(detail != null ? detail : status.getDescription(), cause);
        this.status = status;
        this.detail = detail;
    }

    public static BusinessException of(StatusErrorResponse status) {
        return new BusinessException(status, null, null);
    }

    public static BusinessException of(StatusErrorResponse status, String detail) {
        return new BusinessException(status, detail, null);
    }


    public static BusinessException notFound() {
        return of(StatusErrorResponse.ENTITY_NOT_FOUND);
    }

    public static BusinessException notFound(String detail) {
        return of(StatusErrorResponse.ENTITY_NOT_FOUND, detail);
    }

    public static BusinessException badRequest(String detail) {
        return of(StatusErrorResponse.ILLEGAL_ARGUMENT, detail);
    }

    public static BusinessException duplicate(String detail) {
        return of(StatusErrorResponse.DUPLICATE_RESOURCE, detail);
    }

    public static BusinessException badCredentials(String detail) {
        return of(StatusErrorResponse.INVALID_CREDENTIALS, detail);
    }

    public static BusinessException forbidden() {
        return of(StatusErrorResponse.FORBIDDEN);
    }

    public static BusinessException forbidden(String detail) {
        return of(StatusErrorResponse.FORBIDDEN, detail);
    }
}