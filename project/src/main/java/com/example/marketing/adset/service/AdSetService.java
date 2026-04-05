package com.example.marketing.adset.service;

import com.example.marketing.ad.mapper.AdMapper;
import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.adset.mapper.AdSetMapper;
import com.example.marketing.adset.repository.AdSetRepository;
import com.example.marketing.adset.strategy.AdSetStrategy;
import com.example.marketing.adset.strategy.AdSetStrategyRegistry;
import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.campaign.repository.CampaignRepository;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.cache.PlatformRawDataCache;
import com.example.marketing.infrastructure.service.platformserviceimpl.AbstractPlatformService;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdSetService extends AbstractPlatformService<AdSetEntity, AdSetDto, AdSetStrategy> {

    private final AdSetRepository adSetRepository;
    private final AdSetMapper adSetMapper;
    private final AdMapper adMapper;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final com.example.marketing.ad.repository.AdRepository adRepository;
    private final com.example.marketing.ad.strategy.AdStrategyRegistry adStrategyRegistry;

    public AdSetService(
            AdSetRepository repo,
            AdSetMapper adSetMapper,
            AdMapper adMapper,
            CampaignRepository campaignRepository,
            UserRepository userRepository,
            com.example.marketing.ad.repository.AdRepository adRepository,
            com.example.marketing.ad.strategy.AdStrategyRegistry adStrategyRegistry,
            PlatformClientRegistry clients,
            TokenService tokens,
            AdSetStrategyRegistry strategyRegistry,
            PlatformRawDataCache rawDataCache
    ) {
        super(repo, adSetMapper::convertToBaseDto, clients, tokens, strategyRegistry::of, rawDataCache);
        this.adSetRepository = repo;
        this.adSetMapper = adSetMapper;
        this.adMapper = adMapper;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.adRepository = adRepository;
        this.adStrategyRegistry = adStrategyRegistry;
    }

    @Transactional
    public AdSetDto createAdSet(AdSetDto dto) {
        UserEntity user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + dto.getUserId()));

        CampaignEntity campaign = campaignRepository.findById(dto.getCampaignId())
                .orElseThrow(() -> BusinessException.notFound("Campaign not found with id: " + dto.getCampaignId()));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw BusinessException.forbidden("Campaign with id " + dto.getCampaignId()
                    + " does not belong to user " + dto.getUserId());
        }
        if (campaign.getExternalId() == null || campaign.getExternalId().isBlank()) {
            throw BusinessException.badRequest("Parent campaign with id " + dto.getCampaignId()
                    + " must be synced to the platform before creating an ad set (missing externalId)");
        }

        AdSetEntity e = new AdSetEntity();
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setCampaign(campaign);
        e.setCampaignExternalId(campaign.getExternalId());

        e.setUser(user);
        e.setPlatform(campaign.getPlatform());
        e.setAdAccountId(campaign.getAdAccountId());
        e.setUpdatedAt(LocalDateTime.now());

        adSetRepository.save(e);
        createOnPlatform(e);

        return adSetMapper.convertToBaseDto(e);
    }

    @Transactional
    public AdSetDto updateAdSet(Long id, AdSetDto dto) {
        AdSetEntity e = adSetRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ad set not found with id: " + id));

        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setUpdatedAt(LocalDateTime.now());

        adSetRepository.save(e);

        if (e.getExternalId() != null && !e.getExternalId().isBlank()) {
            updateOnPlatform(e);
        }

        return adSetMapper.convertToBaseDto(e);
    }

    @Transactional
    public void deleteAdSet(Long id) {
        AdSetEntity e = adSetRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ad set not found with id: " + id));

        if (e.getExternalId() != null && !e.getExternalId().isBlank()) {
            deleteOnPlatform(e);
        }
        adSetRepository.delete(e);
    }

    public List<AdSetDto> getAllAdSets(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return adSetRepository.findByUser(user).stream()
                .map(adSet -> {
                    AdSetDto dto = adSetMapper.convertToBaseDto(adSet);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getAdSet(
                                adSet.getPlatform(), adSet.getAdAccountId(), adSet.getExternalId()));
                    }

                    List<com.example.marketing.ad.entity.AdEntity> adEntities = adRepository.findByAdSetId(adSet.getId());
                    dto.setAds(adMapper.convertToBaseDto(adEntities));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<AdSetDto> getAllAdSetsByPlatform(Long userId, Provider platform) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return adSetRepository.findByUserAndPlatform(user, platform.toString()).stream()
                .map(adSet -> {
                    AdSetDto dto = adSetMapper.convertToBaseDto(adSet);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getAdSet(
                                adSet.getPlatform(), adSet.getAdAccountId(), adSet.getExternalId()));
                    }

                    List<com.example.marketing.ad.entity.AdEntity> adEntities = adRepository.findByAdSetId(adSet.getId());
                    dto.setAds(adMapper.convertToBaseDto(adEntities));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public AdSetDto updateByExternalId(Long userId, Provider platform, String externalId, AdSetDto dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        AdSetEntity e = adSetRepository
                .findByUserAndPlatformAndExternalId(user, platform.name(), externalId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Ad set not found with externalId '" + externalId + "' on platform " + platform));

        if (dto.getName() != null && !dto.getName().isBlank()) e.setName(dto.getName());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) e.setStatus(dto.getStatus());
        e.setUpdatedAt(LocalDateTime.now());

        adSetRepository.save(e);
        updateOnPlatform(e);

        return adSetMapper.convertToBaseDto(e);
    }

    public SyncResult syncAdSets(Long userId, Provider platform, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        SyncResult adSetResult = syncFromPlatform(user, platform, adAccountId);
        log.info("Synced {} ad sets (inserted: {}, updated: {})",
                adSetResult.fetched(),
                adSetResult.inserted(),
                adSetResult.updated());

        syncAdsForAdSets(user, platform, adAccountId);

        return adSetResult;
    }

    /**
     * Syncs ads for all ad sets belonging to the user/platform/account.
     * This is called automatically after syncing ad sets to ensure child ads are populated.
     */
    @Transactional
    public void syncAdsForAdSets(UserEntity user, Provider platform, String adAccountId) {
        try {
            log.info("Cascading sync: fetching ads for ad sets in account {}", adAccountId);

            com.example.marketing.ad.strategy.AdStrategy adStrategy = adStrategyRegistry.of(platform);
            String accessToken = tokens.getAccessToken(user, platform);
            var client = clients.of(platform);

            String after = null;
            Map<String, String> baseQ = adStrategy.baseListQuery();
            List<com.example.marketing.ad.dto.AdDto> adDtos = new ArrayList<>();

            do {
                Map<String, String> q = new HashMap<>(baseQ);
                if (after != null) q.put("after", after);

                ResponseEntity<Map> resp = client.get(adStrategy.listPath(adAccountId), q, accessToken);
                Map<String, Object> body = resp.getBody();
                if (body == null) break;

                @SuppressWarnings("unchecked")
                var data = (List<Map<String, Object>>) body.get("data");
                if (data != null) {
                    for (var row : data) {
                        adDtos.add(adStrategy.mapGetRow(row, user.getId(), adAccountId));
                    }
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> paging = (Map<String, Object>) body.get("paging");
                @SuppressWarnings("unchecked")
                Map<String, Object> cursors = paging != null ? (Map<String, Object>) paging.get("cursors") : null;
                after = cursors != null ? (String) cursors.get("after") : null;

            } while (after != null);

            log.info("Fetched {} ads from platform", adDtos.size());

            List<String> externalIds = adDtos.stream()
                    .map(com.example.marketing.ad.dto.AdDto::getExternalId)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            if (externalIds.isEmpty()) {
                log.info("No ads to sync");
                return;
            }

            List<com.example.marketing.ad.entity.AdEntity> existing = adRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
                    user, platform.name(), adAccountId, externalIds);
            Map<String, com.example.marketing.ad.entity.AdEntity> existingById = existing.stream()
                    .filter(e -> e.getExternalId() != null)
                    .collect(Collectors.toMap(
                            com.example.marketing.ad.entity.AdEntity::getExternalId,
                            Function.identity(),
                            (a, b) -> a));

            int inserted = 0;

            for (com.example.marketing.ad.dto.AdDto dto : adDtos) {
                try {
                    String ext = dto.getExternalId();
                    if (ext == null || ext.isBlank()) continue;
                    ext = ext.trim();

                    if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                        rawDataCache.putAd(platform.name(), adAccountId, ext, dto.getRawData());
                    }

                    com.example.marketing.ad.entity.AdEntity adEntity = existingById.get(ext);
                    boolean needsSave = false;

                    if (adEntity == null) {
                        adEntity = new com.example.marketing.ad.entity.AdEntity();
                        adEntity.setUser(user);
                        adEntity.setPlatform(platform.name());
                        adEntity.setAdAccountId(adAccountId);
                        adEntity.setExternalId(ext);
                        adEntity.setCreatedAt(LocalDateTime.now());

                        adEntity.setName(dto.getName());
                        adEntity.setStatus(dto.getStatus());
                        adEntity.setAdSetExternalId(dto.getAdSetExternalId());
                        adEntity.setCreativeId(dto.getCreativeId());
                        if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                            adEntity.setRawData(dto.getRawData());
                        }
                        adEntity.setUpdatedAt(LocalDateTime.now());

                        inserted++;
                        needsSave = true;
                    } else {
                        log.debug("Ad {} already exists, updating data", ext);
                        adEntity.setName(dto.getName());
                        adEntity.setStatus(dto.getStatus());
                        if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                            adEntity.setRawData(dto.getRawData());
                        }
                        adEntity.setUpdatedAt(LocalDateTime.now());
                        needsSave = true;
                    }

                    if (dto.getAdSetExternalId() != null) {
                        Optional<AdSetEntity> adSetOpt = adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                                user, platform.name(), adAccountId, dto.getAdSetExternalId());
                        if (adSetOpt.isPresent()) {
                            AdSetEntity adSet = adSetOpt.get();
                            adEntity.setAdSet(adSet);
                            adEntity.setAdSetExternalId(dto.getAdSetExternalId());
                            adEntity.setAdSetName(adSet.getName());
                            needsSave = true;
                        }
                    }

                    if (needsSave) {
                        adRepository.save(adEntity);
                    }

                } catch (Exception e) {
                    log.error("Failed to sync ad {}: {}", dto.getExternalId(), e.getMessage());
                }
            }

            log.info("Completed cascading sync of ads (inserted: {}, skipped existing: {})", inserted, externalIds.size() - inserted);

        } catch (Exception e) {
            log.error("Failed to cascade sync ads: {}", e.getMessage(), e);
        }
    }

    @Override protected AdSetEntity newEntity() { return new AdSetEntity(); }

    @Override protected String dtoExternalId(AdSetDto dto) { return dto.getExternalId(); }

    @Override
    protected Map<String, Object> dtoRawData(AdSetDto dto) { return dto.getRawData(); }

    @Override
    protected void applyRawDataToEntity(AdSetEntity entity, Map<String, Object> raw) { entity.setRawData(raw); }

    @Override
    protected void applyDtoToNewEntity(AdSetDto dto, AdSetEntity e, UserEntity user, Provider provider, String adAccountId) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setCampaignExternalId(dto.getCampaignExternalId());

        if (dto.getCampaignExternalId() != null) {
            campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                            user, provider.name(), adAccountId, dto.getCampaignExternalId())
                    .ifPresent(e::setCampaign);
        }
    }

    @Override
    protected void applyDtoToExistingEntity(AdSetDto dto, AdSetEntity e) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setCampaignExternalId(dto.getCampaignExternalId());

        if (dto.getCampaignExternalId() != null) {
            campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                            e.getUser(), e.getPlatform(), e.getAdAccountId(), dto.getCampaignExternalId())
                    .ifPresent(e::setCampaign);
        }
    }

    @Override
    protected void cacheRawData(String platform, String adAccountId, String externalId, Map<String, Object> raw) {
        rawDataCache.putAdSet(platform, adAccountId, externalId, raw);
    }

    @Override
    protected List<AdSetEntity> findExisting(UserEntity user, String platform, String adAccountId, Collection<String> externalIds) {
        return adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, platform, adAccountId, externalIds);
    }

    /**
     * Get ads for a specific ad set, filtered by platform.
     */
    public List<com.example.marketing.ad.dto.AdDto> getAdsByAdSet(Long userId, Provider platform, Long adSetId) {
        userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        AdSetEntity adSet = adSetRepository.findById(adSetId)
                .orElseThrow(() -> BusinessException.notFound("Ad set not found with id: " + adSetId));

        if (!adSet.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("Ad set with id " + adSetId + " does not belong to user " + userId);
        }
        if (!adSet.getPlatform().equals(platform.name())) {
            throw BusinessException.badRequest("Ad set platform '" + adSet.getPlatform()
                    + "' does not match requested platform '" + platform.name() + "'");
        }

        List<com.example.marketing.ad.entity.AdEntity> adEntities = adRepository.findByAdSetId(adSetId);
        return adMapper.convertToBaseDto(adEntities);
    }
}
