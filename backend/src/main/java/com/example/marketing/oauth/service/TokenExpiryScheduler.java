package com.example.marketing.oauth.service;

import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.oauth.entity.TokenStatus;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenExpiryScheduler.class);

    private final OAuthAccountRepository oauthAccountRepo;
    private final TokenExchangeService tokenExchangeService;

    // Runs every day at 08:00
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void updateTokenStatuses() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soonThreshold = now.plusDays(2);

        List<OAuthAccountEntity> accounts = oauthAccountRepo.findAll();
        log.info("Token expiry check: {} accounts to evaluate", accounts.size());

        for (OAuthAccountEntity account : accounts) {
            if (account.getTokenExpiry() == null) {
                // Development mode token — no expiry set, leave as VALID
                account.setTokenStatus(TokenStatus.VALID);
                continue;
            }

            TokenStatus previous = account.getTokenStatus();

            if (account.getTokenExpiry().isBefore(now)) {
                account.setTokenStatus(TokenStatus.EXPIRED);
            } else if (account.getTokenExpiry().isBefore(soonThreshold)) {
                account.setTokenStatus(TokenStatus.EXPIRING_SOON);
            } else {
                account.setTokenStatus(TokenStatus.VALID);
            }

            if (previous != account.getTokenStatus()) {
                log.info("Token status changed for userId={} provider={}: {} → {}",
                        account.getUser().getId(), account.getProvider(), previous, account.getTokenStatus());
            }
        }

        oauthAccountRepo.saveAll(accounts);

        // Attempt proactive refresh for tokens that are expiring soon
        accounts.stream()
                .filter(a -> a.getTokenStatus() == TokenStatus.EXPIRING_SOON)
                .forEach(this::tryRefreshToken);
    }

    private void tryRefreshToken(OAuthAccountEntity account) {
        try {
            TokenExchangeService.TokenExchangeResult refreshed =
                    tokenExchangeService.exchangeToLongLived(
                            com.example.marketing.infrastructure.util.Provider.valueOf(account.getProvider()),
                            account.getAccessToken()
                    );

            account.setAccessToken(refreshed.accessToken());
            account.setTokenExpiry(refreshed.expiresAt());
            account.setTokenStatus(TokenStatus.VALID);
            oauthAccountRepo.save(account);

            log.info("Proactively refreshed token for userId={} provider={}",
                    account.getUser().getId(), account.getProvider());

        } catch (Exception e) {
            log.warn("Proactive token refresh failed for userId={} provider={}: {}",
                    account.getUser().getId(), account.getProvider(), e.getMessage());
            account.setTokenStatus(TokenStatus.EXPIRED);
            oauthAccountRepo.save(account);
        }
    }
}
