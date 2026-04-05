package com.example.marketing.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class CookieUtil {

    private static final String OAUTH_STATE_COOKIE = "oauth_connect_state";

    public static void setOauthState(HttpServletResponse response, String state) {
        ResponseCookie cookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(false)     // true in prod
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(10))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearOauthState(HttpServletResponse response) {
        clearCookie(response, OAUTH_STATE_COOKIE, "/");
        clearCookie(response, OAUTH_STATE_COOKIE, "/api");
    }

    public static void clearCookie(HttpServletResponse response, String name, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false)     // true in prod
                .path(path)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

