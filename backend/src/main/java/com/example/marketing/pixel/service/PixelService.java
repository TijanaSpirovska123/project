package com.example.marketing.pixel.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.page.util.CursorPager;
import com.example.marketing.pixel.dto.PixelDto;
import com.example.marketing.pixel.strategy.PixelStrategy;
import com.example.marketing.pixel.strategy.PixelStrategyRegistry;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pixels are just a reference list for the "Meta Pixel" dropdown — fetched live from Meta
 * on each request rather than persisted, since nothing else in the app needs to relate to
 * them (the chosen id is stored as a plain string on the Ad, not a foreign key).
 */
@Service
@RequiredArgsConstructor
public class PixelService {

    private static final Provider PLATFORM = Provider.META;

    private final UserRepository userRepository;
    private final TokenService tokens;
    private final PlatformClientRegistry clients;
    private final PixelStrategyRegistry pixelStrategies;

    public List<PixelDto> getAllPixelsFromMeta(Long userId, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String token = tokens.getAccessToken(user, PLATFORM);
        var client = clients.of(PLATFORM);
        PixelStrategy strategy = pixelStrategies.of(PLATFORM);

        List<Map<String, Object>> rows = CursorPager.fetchAll(
                client,
                strategy.listPixelsPath(adAccountId),
                strategy.listPixelsQuery(),
                token
        );

        return rows.stream()
                .map(row -> new PixelDto(
                        Objects.toString(row.get("id"), null),
                        Objects.toString(row.get("name"), null)))
                .filter(p -> p.getPixelId() != null)
                .toList();
    }
}
