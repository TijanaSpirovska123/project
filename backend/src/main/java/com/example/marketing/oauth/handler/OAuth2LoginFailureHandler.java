package com.example.marketing.oauth.handler;

import com.example.marketing.oauth.config.FrontendProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("OAuth2 authentication failed: {}", exception.getMessage());

        // Clear the state cookie so a stale cookie can't block the next connect attempt.
        Cookie cookie = new Cookie("oauth_connect_state", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.sendRedirect(frontendProperties.getSuccessRedirectUrl()
                + "?provider=UNKNOWN&status=auth_failed");
    }
}
