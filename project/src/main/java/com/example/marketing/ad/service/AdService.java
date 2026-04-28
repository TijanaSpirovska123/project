package com.example.marketing.ad.service;

import com.example.marketing.ad.dto.AdDto;
import com.example.marketing.ad.entity.AdEntity;
import com.example.marketing.ad.mapper.AdMapper;
import com.example.marketing.ad.repository.AdRepository;
import com.example.marketing.ad.strategy.AdStrategy;
import com.example.marketing.ad.strategy.AdStrategyRegistry;
import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.adset.repository.AdSetRepository;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.cache.PlatformRawDataCache;
import com.example.marketing.infrastructure.service.platformserviceimpl.AbstractPlatformService;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdService extends AbstractPlatformService<AdEntity, AdDto, AdStrategy> {

    private final AdRepository adRepository;
    private final AdSetRepository adSetRepository;
    private final AdMapper adMapper;
    private final UserRepository userRepository;

    public AdService(
            AdRepository repo,
            AdMapper adMapper,
            AdSetRepository adSetRepository,
            UserRepository userRepository,
            PlatformClientRegistry clients,
            TokenService tokens,
            AdStrategyRegistry strategyRegistry,
            PlatformRawDataCache rawDataCache
    ) {
        super(repo, adMapper::convertToBaseDto, clients, tokens, strategyRegistry::of, rawDataCache);
        this.adRepository = repo;
        this.adMapper = adMapper;
        this.adSetRepository = adSetRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AdDto createAd(Long userId, AdDto dto) {
        if (dto == null) throw new IllegalArgumentException("Body is required");
        if (dto.getName() == null || dto.getName().isBlank()) throw new IllegalArgumentException("name is required");
        if (dto.getCreativeId() == null || dto.getCreativeId().isBlank()) throw new IllegalArgumentException("creativeId is required");

        boolean hasLocalId = dto.getAdSetId() != null;
        boolean hasExternalId = dto.getAdSetExternalId() != null && !dto.getAdSetExternalId().isBlank();
        if (!hasLocalId && !hasExternalId) {
            throw new IllegalArgumentException("Either adSetId (local DB id) or adSetExternalId (platform id) is required");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        AdSetEntity adSet;
        if (hasLocalId) {
            adSet = adSetRepository.findById(dto.getAdSetId())
                    .orElseThrow(() -> BusinessException.notFound("Ad set not found with id: " + dto.getAdSetId()));
        } else {
            String platform = dto.getPlatform() != null ? dto.getPlatform() : "META";
            String adAccountId = dto.getAdAccountId();
            if (adAccountId != null && !adAccountId.isBlank()) {
                adSet = adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                                user, platform, adAccountId, dto.getAdSetExternalId())
                        .orElseThrow(() -> BusinessException.notFound("Ad set not found with externalId '"
                                + dto.getAdSetExternalId() + "' for platform " + platform + " and account " + adAccountId));
            } else {
                adSet = adSetRepository.findByUserAndPlatformAndExternalId(
                                user, platform, dto.getAdSetExternalId())
                        .orElseThrow(() -> BusinessException.notFound("Ad set not found with externalId '"
                                + dto.getAdSetExternalId() + "' for platform " + platform));
            }
        }

        if (!adSet.getUser().getId().equals(user.getId())) {
            throw BusinessException.forbidden("Ad set with id " + adSet.getId() + " does not belong to user " + userId);
        }
        if (adSet.getExternalId() == null || adSet.getExternalId().isBlank()) {
            throw BusinessException.badRequest("Ad set with id " + adSet.getId()
                    + " must be synced to the platform before creating an ad (missing externalId)");
        }

        AdEntity e = new AdEntity();
        e.setAdSet(adSet);
        e.setAdSetExternalId(adSet.getExternalId());
        e.setAdSetName(adSet.getName());

        e.setName(dto.getName());
        e.setStatus(dto.getStatus() != null ? dto.getStatus() : "PAUSED");
        e.setCreativeId(dto.getCreativeId());

        e.setUser(user);
        e.setPlatform(adSet.getPlatform());
        e.setAdAccountId(adSet.getAdAccountId());
        e.setUpdatedAt(LocalDateTime.now());

        e = adRepository.save(e);
        createOnPlatform(e);

        return adMapper.convertToBaseDto(e);
    }

    @Transactional
    public AdDto updateAd(Long userId, Long id, AdDto dto) {
        AdEntity e = adRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ad not found with id: " + id));

        if (!e.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("Ad with id " + id + " does not belong to user " + userId);
        }
        if (dto == null) return adMapper.convertToBaseDto(e);

        if (dto.getName() != null && !dto.getName().isBlank()) e.setName(dto.getName());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) e.setStatus(dto.getStatus());

        if (e.getAdSet() != null) {
            e.setAdSetName(e.getAdSet().getName());
        }

        e.setUpdatedAt(LocalDateTime.now());
        e = adRepository.save(e);

        if (e.getExternalId() != null && !e.getExternalId().isBlank()) {
            updateOnPlatform(e);
        }

        return adMapper.convertToBaseDto(e);
    }

    @Transactional
    public void deleteAd(Long userId, Long id) {
        AdEntity e = adRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ad not found with id: " + id));

        if (!e.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("Ad with id " + id + " does not belong to user " + userId);
        }

        if (e.getExternalId() != null && !e.getExternalId().isBlank()) {
            deleteOnPlatform(e);
        }
        adRepository.delete(e);
    }

    public List<AdDto> getAllAds(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return adRepository.findByUser(user).stream()
                .map(ad -> {
                    AdDto dto = adMapper.convertToBaseDto(ad);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getAd(
                                ad.getPlatform(), ad.getAdAccountId(), ad.getExternalId()));
                    }
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public List<AdDto> getAllAdsByPlatform(Long userId, Provider platform) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return adRepository.findByUserAndPlatform(user, platform.toString()).stream()
                .map(ad -> {
                    AdDto dto = adMapper.convertToBaseDto(ad);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getAd(
                                ad.getPlatform(), ad.getAdAccountId(), ad.getExternalId()));
                    }
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public List<AdDto> getAllAdsFromPlatform(Long userId, Provider provider, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return listFromPlatform(user, provider, adAccountId);
    }

    @Transactional
    public AdDto updateByExternalId(Long userId, Provider platform, String externalId, AdDto dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        AdEntity e = adRepository
                .findByUserAndPlatformAndExternalId(user, platform.name(), externalId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Ad not found with externalId '" + externalId + "' on platform " + platform));

        if (dto.getName() != null && !dto.getName().isBlank()) e.setName(dto.getName());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) e.setStatus(dto.getStatus());
        e.setUpdatedAt(LocalDateTime.now());

        adRepository.save(e);
        updateOnPlatform(e);

        return adMapper.convertToBaseDto(e);
    }

    public SyncResult syncAds(Long userId, Provider platform, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));
        return syncFromPlatform(user, platform, adAccountId);
    }

    @Override protected AdEntity newEntity() { return new AdEntity(); }

    @Override protected String dtoExternalId(AdDto dto) { return dto.getExternalId(); }

    @Override
    protected Map<String, Object> dtoRawData(AdDto dto) { return dto.getRawData(); }

    @Override
    protected void applyRawDataToEntity(AdEntity entity, Map<String, Object> raw) { entity.setRawData(raw); }

    @Override
    protected void applyDtoToNewEntity(AdDto dto, AdEntity e, UserEntity user, Provider provider, String adAccountId) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setAdSetExternalId(dto.getAdSetExternalId());
        e.setCreativeId(dto.getCreativeId());

        if (dto.getAdSetExternalId() != null) {
            adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                            user, provider.name(), adAccountId, dto.getAdSetExternalId())
                    .ifPresent(adSet -> {
                        e.setAdSet(adSet);
                        e.setAdSetName(adSet.getName());
                    });
        }
    }

    @Override
    protected void applyDtoToExistingEntity(AdDto dto, AdEntity e) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setAdSetExternalId(dto.getAdSetExternalId());
        if (dto.getCreativeId() != null && !dto.getCreativeId().isBlank()) e.setCreativeId(dto.getCreativeId());

        if (dto.getAdSetExternalId() != null) {
            adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                            e.getUser(), e.getPlatform(), e.getAdAccountId(), dto.getAdSetExternalId())
                    .ifPresent(adSet -> {
                        e.setAdSet(adSet);
                        e.setAdSetName(adSet.getName());
                    });
        }
    }

    @Override
    protected void cacheRawData(String platform, String adAccountId, String externalId, Map<String, Object> raw) {
        rawDataCache.putAd(platform, adAccountId, externalId, raw);
    }

    @Override
    protected List<AdEntity> findExisting(UserEntity user, String platform, String adAccountId, Collection<String> externalIds) {
        return adRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, platform, adAccountId, externalIds);
    }

    @Override
    protected void deleteAllByUserAndPlatformAndAdAccount(Long userId, String platform, String adAccountId) {
        adRepository.deleteAllByUserIdAndPlatformAndAdAccountId(userId, platform, adAccountId);
    }

    @Override
    protected void deleteAllByUserAndPlatform(Long userId, String platform) {
        adRepository.deleteAllByUserIdAndPlatform(userId, platform);
    }
}
