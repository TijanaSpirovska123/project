package com.example.marketing.userconfig.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.example.marketing.userconfig.dto.ColumnConfigDto;
import com.example.marketing.userconfig.dto.InsightsConfigDto;
import com.example.marketing.userconfig.dto.ThemeConfigDto;
import com.example.marketing.userconfig.entity.UserConfigEntity;
import com.example.marketing.userconfig.repository.UserConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserConfigService {

    private static final String INSIGHTS_METRICS = "INSIGHTS_METRICS";
    private static final String COLUMN_CONFIG_PREFIX = "COLUMN_CONFIG_";
    private static final String THEME_CONFIG = "THEME_CONFIG";

    private static final List<String> DEFAULT_METRIC_CARDS =
            List.of("impressions", "reach", "clicks", "spend", "ctr", "cpm");

    private final UserConfigRepository configRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InsightsConfigDto getInsightsConfig(Long userId) {
        UserEntity user = getUser(userId);
        Optional<UserConfigEntity> config = configRepository.findByUserAndConfigType(user, INSIGHTS_METRICS);
        if (config.isEmpty()) {
            InsightsConfigDto defaults = new InsightsConfigDto();
            defaults.setMetricCards(DEFAULT_METRIC_CARDS);
            return defaults;
        }
        return deserialize(config.get().getConfigJson(), InsightsConfigDto.class);
    }

    @Transactional
    public InsightsConfigDto saveInsightsConfig(Long userId, InsightsConfigDto dto) {
        UserEntity user = getUser(userId);
        upsert(user, INSIGHTS_METRICS, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public ColumnConfigDto getColumnConfig(Long userId, String entityType) {
        UserEntity user = getUser(userId);
        String configType = COLUMN_CONFIG_PREFIX + entityType.toUpperCase();
        Optional<UserConfigEntity> config = configRepository.findByUserAndConfigType(user, configType);
        if (config.isEmpty()) {
            return null; // frontend uses defaults when null
        }
        return deserialize(config.get().getConfigJson(), ColumnConfigDto.class);
    }

    @Transactional(readOnly = true)
    public ThemeConfigDto getThemeConfig(Long userId) {
        UserEntity user = getUser(userId);
        Optional<UserConfigEntity> config = configRepository.findByUserAndConfigType(user, THEME_CONFIG);
        if (config.isEmpty()) {
            return null; // frontend uses localStorage default when null
        }
        return deserialize(config.get().getConfigJson(), ThemeConfigDto.class);
    }

    @Transactional
    public ThemeConfigDto saveThemeConfig(Long userId, ThemeConfigDto dto) {
        UserEntity user = getUser(userId);
        upsert(user, THEME_CONFIG, dto);
        return dto;
    }

    @Transactional
    public ColumnConfigDto saveColumnConfig(Long userId, ColumnConfigDto dto) {
        if (dto.getEntityType() == null || dto.getEntityType().isBlank()) {
            throw BusinessException.badRequest("entityType is required");
        }
        UserEntity user = getUser(userId);
        String configType = COLUMN_CONFIG_PREFIX + dto.getEntityType().toUpperCase();
        upsert(user, configType, dto);
        return dto;
    }

    private void upsert(UserEntity user, String configType, Object value) {
        String json = serialize(value);
        UserConfigEntity entity = configRepository.findByUserAndConfigType(user, configType)
                .orElseGet(() -> {
                    UserConfigEntity e = new UserConfigEntity();
                    e.setUser(user);
                    e.setConfigType(configType);
                    return e;
                });
        entity.setConfigJson(json);
        entity.setUpdatedAt(LocalDateTime.now());
        configRepository.save(entity);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize config", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize config", e);
        }
    }
}
