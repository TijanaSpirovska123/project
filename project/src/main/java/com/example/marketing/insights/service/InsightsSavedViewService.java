package com.example.marketing.insights.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.InsightsSavedViewDto;
import com.example.marketing.insights.entity.InsightsSavedViewEntity;
import com.example.marketing.insights.mapper.InsightsSavedViewMapper;
import com.example.marketing.insights.repository.InsightsSavedViewRepository;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightsSavedViewService {

    private final InsightsSavedViewRepository repository;
    private final UserRepository userRepository;
    private final InsightsSavedViewMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public InsightsSavedViewDto create(Long userId, InsightsSavedViewDto dto) {
        validate(dto);
        UserEntity user = getUserOrThrow(userId);
        Provider provider = parseProvider(dto.getProvider());

        if (repository.existsByUserAndName(user, dto.getName())) {
            throw BusinessException.duplicate("A saved view named '" + dto.getName() + "' already exists for this provider.");
        }

        InsightsSavedViewEntity entity = mapper.toEntity(dto);
        entity.setUser(user);
        entity.setProvider(provider);
        entity.setViewConfig(serializeViewConfig(dto.getViewConfig()));
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDto(repository.save(entity));
    }

    @Transactional
    public InsightsSavedViewDto update(Long userId, Long viewId, InsightsSavedViewDto dto) {
        validate(dto);
        UserEntity user = getUserOrThrow(userId);
        InsightsSavedViewEntity entity = repository.findByIdAndUser(viewId, user)
                .orElseThrow(BusinessException::notFound);

        if (repository.existsByUserAndNameAndIdNot(user, dto.getName(), viewId)) {
            throw BusinessException.duplicate("A saved view named '" + dto.getName() + "' already exists.");
        }

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        if (dto.getProvider() != null) entity.setProvider(parseProvider(dto.getProvider()));
        if (dto.getViewConfig() != null) entity.setViewConfig(serializeViewConfig(dto.getViewConfig()));
        entity.setPinned(dto.isPinned());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(Long userId, Long viewId) {
        UserEntity user = getUserOrThrow(userId);
        InsightsSavedViewEntity entity = repository.findByIdAndUser(viewId, user)
                .orElseThrow(BusinessException::notFound);
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<InsightsSavedViewDto> listForProvider(Long userId, String providerStr) {
        UserEntity user = getUserOrThrow(userId);
        Provider provider = parseProvider(providerStr);
        return repository.findByUserAndProviderOrderByPinnedDescUpdatedAtDesc(user, provider)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public InsightsSavedViewDto getById(Long userId, Long viewId) {
        UserEntity user = getUserOrThrow(userId);
        return toDto(repository.findByIdAndUser(viewId, user)
                .orElseThrow(BusinessException::notFound));
    }

    @Transactional
    public InsightsSavedViewDto togglePin(Long userId, Long viewId) {
        UserEntity user = getUserOrThrow(userId);
        InsightsSavedViewEntity entity = repository.findByIdAndUser(viewId, user)
                .orElseThrow(BusinessException::notFound);
        entity.setPinned(!entity.isPinned());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(entity));
    }

    private void validate(InsightsSavedViewDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw BusinessException.badRequest("View name is required.");
        }
        if (dto.getName().length() > 128) {
            throw BusinessException.badRequest("View name must not exceed 128 characters.");
        }
        if (dto.getProvider() == null || dto.getProvider().isBlank()) {
            throw BusinessException.badRequest("Provider is required.");
        }
        if (dto.getViewConfig() == null) {
            throw BusinessException.badRequest("viewConfig is required.");
        }
    }

    private String serializeViewConfig(JsonNode node) {
        if (node == null) throw BusinessException.badRequest("viewConfig must be valid JSON.");
        return node.toString();
    }

    private Provider parseProvider(String providerStr) {
        try {
            return Provider.valueOf(providerStr.toUpperCase());
        } catch (Exception e) {
            throw BusinessException.badRequest("Unknown provider: " + providerStr);
        }
    }

    private UserEntity getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    private InsightsSavedViewDto toDto(InsightsSavedViewEntity entity) {
        InsightsSavedViewDto dto = mapper.toDto(entity);
        dto.setProvider(entity.getProvider() != null ? entity.getProvider().name() : null);
        return dto;
    }
}
