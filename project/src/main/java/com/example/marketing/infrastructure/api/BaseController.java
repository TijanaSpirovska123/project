package com.example.marketing.infrastructure.api;

import lombok.Data;
import org.springframework.security.core.Authentication;

public abstract class BaseController {

    protected <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(data, true, null);
    }

    protected static <T> BaseResponse<T> notFound(String message) {
        return new BaseResponse<>(null, false, message);
    }

    @Data
    public static class BaseResponse<T> {
        private T data;
        private boolean success;
        private String error;

        public BaseResponse(T data) {
            this.data = data;
            this.success = true;
            this.error = null;
        }

        public BaseResponse(T data, boolean success, String error) {
            this.data = data;
            this.success = success;
            this.error = error;
        }
    }

    protected Long extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new RuntimeException("Unauthorized");
        if (auth.getPrincipal() instanceof com.example.marketing.auth.UserPrincipal p) return p.userId();
        throw new RuntimeException("Invalid principal");
    }
}
