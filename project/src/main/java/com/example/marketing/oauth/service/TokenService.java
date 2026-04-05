package com.example.marketing.oauth.service;


import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.example.marketing.exception.BusinessException.*;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final OAuthAccountRepository repo;

    public String getAccessToken(UserEntity user, Provider provider) {
        OAuthAccountEntity oauth = repo.findByUserAndProvider(user, provider.name())
                .orElseThrow(() -> of(StatusErrorResponse.INVALID_CREDENTIALS,
                        "No " + provider + " connection for user " + user.getId()));

        if (oauth.getTokenExpiry() != null && oauth.getTokenExpiry().isBefore(LocalDateTime.now().plusDays(2))) {
            throw of(StatusErrorResponse.META_TOKEN_EXPIRED);
        }

        return oauth.getAccessToken();
    }
}

