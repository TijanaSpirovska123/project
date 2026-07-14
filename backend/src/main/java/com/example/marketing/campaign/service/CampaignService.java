package com.example.marketing.campaign.service;

import com.example.marketing.ad.mapper.AdMapper;
import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.adset.mapper.AdSetMapper;
import com.example.marketing.adset.repository.AdSetRepository;
import com.example.marketing.adset.strategy.AdSetStrategy;
import com.example.marketing.adset.strategy.AdSetStrategyRegistry;
import com.example.marketing.campaign.dto.BulkStatusRequestDto;
import com.example.marketing.campaign.dto.CampaignDto;
import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.campaign.mapper.CampaignMapper;
import com.example.marketing.campaign.repository.CampaignRepository;
import com.example.marketing.campaign.strategy.CampaignStrategy;
import com.example.marketing.campaign.strategy.CampaignStrategyRegistry;
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
public class CampaignService extends AbstractPlatformService<
        CampaignEntity,
        CampaignDto,
        CampaignStrategy
        > {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;
    private final AdSetRepository adSetRepository;
    private final AdSetMapper adSetMapper;
    private final AdMapper adMapper;
    private final UserRepository userRepository;
    private final AdSetStrategyRegistry adSetStrategyRegistry;
    private final com.example.marketing.ad.repository.AdRepository adRepository;

    public CampaignService(CampaignRepository repo,
                           CampaignMapper campaignMapper,
                           AdSetRepository adSetRepository,
                           AdSetMapper adSetMapper,
                           AdMapper adMapper,
                           UserRepository userRepository,
                           PlatformClientRegistry clients,
                           TokenService tokens,
                           CampaignStrategyRegistry strategyRegistry,
                           AdSetStrategyRegistry adSetStrategyRegistry,
                           com.example.marketing.ad.repository.AdRepository adRepository,
                           PlatformRawDataCache rawDataCache) {

        super(repo, campaignMapper::convertToBaseDto, clients, tokens, strategyRegistry::of, rawDataCache);
        this.campaignRepository = repo;
        this.campaignMapper = campaignMapper;
        this.adSetRepository = adSetRepository;
        this.adSetMapper = adSetMapper;
        this.adMapper = adMapper;
        this.userRepository = userRepository;
        this.adSetStrategyRegistry = adSetStrategyRegistry;
        this.adRepository = adRepository;
    }

    @Transactional
    public CampaignDto createCampaign(CampaignDto dto) {
        UserEntity user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + dto.getUserId()));

        CampaignEntity entity = campaignMapper.convertToBaseEntity(dto);

        entity.setUser(user);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getPlatform() == null) entity.setPlatform("META");
        if (entity.getAdAccountId() == null) entity.setAdAccountId(dto.getAdAccountId());

        campaignRepository.save(entity);
        createOnPlatform(entity);

        return campaignMapper.convertToBaseDto(entity);
    }

    @Transactional
    public CampaignDto updateCampaign(Long id, CampaignDto dto) {
        CampaignEntity e = campaignRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Campaign not found with id: " + id));

        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
        e.setUpdatedAt(LocalDateTime.now());

        campaignRepository.save(e);

        if (e.getExternalId() != null) {
            try {
                updateOnPlatform(e);
            } catch (Exception ex) {
                log.warn("Failed to sync update to platform: {}", ex.getMessage());
            }
        }
        return campaignMapper.convertToBaseDto(e);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        CampaignEntity e = campaignRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Campaign not found with id: " + id));

        if (e.getExternalId() != null) {
            try {
                deleteOnPlatform(e);
            } catch (Exception ex) {
                log.warn("Failed to delete on platform: {}", ex.getMessage());
            }
        }
        campaignRepository.delete(e);
    }

    public List<CampaignDto> getAllCampaigns(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return campaignRepository.findByUser(user).stream()
                .map(campaign -> {
                    CampaignDto dto = campaignMapper.convertToBaseDto(campaign);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getCampaign(
                                campaign.getPlatform(), campaign.getAdAccountId(), campaign.getExternalId()));
                    }

                    List<AdSetEntity> adSetEntities = adSetRepository.findByCampaignId(campaign.getId());
                    dto.setAdSets(adSetEntities.stream()
                            .map(this::toAdSetDtoWithAds)
                            .collect(Collectors.toList()));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<CampaignDto> getAllCampaignsByPlatform(Long userId, Provider platform) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return campaignRepository.findByUserAndPlatform(user, platform.toString()).stream()
                .map(campaign -> {
                    CampaignDto dto = campaignMapper.convertToBaseDto(campaign);
                    if (dto.getRawData() == null) {
                        dto.setRawData(rawDataCache.getCampaign(
                                campaign.getPlatform(), campaign.getAdAccountId(), campaign.getExternalId()));
                    }

                    List<AdSetEntity> adSetEntities = adSetRepository.findByCampaignId(campaign.getId());
                    dto.setAdSets(adSetEntities.stream()
                            .map(this::toAdSetDtoWithAds)
                            .collect(Collectors.toList()));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<CampaignDto> getAllCampaignsFromPlatform(Long userId, Provider provider, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return super.listFromPlatform(user, provider, adAccountId);
    }

    @Override protected CampaignEntity newEntity() { return new CampaignEntity(); }

    @Override protected String dtoExternalId(CampaignDto dto) { return dto.getExternalId(); }

    @Override
    protected Map<String, Object> dtoRawData(CampaignDto dto) { return dto.getRawData(); }

    @Override
    protected void applyRawDataToEntity(CampaignEntity entity, Map<String, Object> raw) { entity.setRawData(raw); }

    @Override
    protected void applyDtoToNewEntity(CampaignDto dto, CampaignEntity e, UserEntity user, Provider provider, String adAccountId) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
    }

    @Override
    protected void applyDtoToExistingEntity(CampaignDto dto, CampaignEntity e) {
        e.setName(dto.getName());
        e.setStatus(dto.getStatus());
    }

    @Override
    protected void cacheRawData(String platform, String adAccountId, String externalId, Map<String, Object> raw) {
        rawDataCache.putCampaign(platform, adAccountId, externalId, raw);
    }

    @Override
    protected List<CampaignEntity> findExisting(UserEntity user, String platform, String adAccountId, Collection<String> externalIds) {
        return campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, platform, adAccountId, externalIds);
    }

    @Override
    protected void deleteAllByUserAndPlatformAndAdAccount(Long userId, String platform, String adAccountId) {
        campaignRepository.deleteAllByUserIdAndPlatformAndAdAccountId(userId, platform, adAccountId);
    }

    @Override
    protected void deleteAllByUserAndPlatform(Long userId, String platform) {
        campaignRepository.deleteAllByUserIdAndPlatform(userId, platform);
    }

    @Transactional
    public CampaignDto updateByExternalId(Long userId, Provider platform, String externalId, CampaignDto dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        CampaignEntity e = campaignRepository
                .findByUserAndPlatformAndExternalId(user, platform.name(), externalId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Campaign not found with externalId '" + externalId + "' on platform " + platform));

        if (dto.getName() != null && !dto.getName().isBlank()) e.setName(dto.getName());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) e.setStatus(dto.getStatus());
        e.setUpdatedAt(LocalDateTime.now());

        campaignRepository.save(e);
        updateOnPlatform(e);

        return campaignMapper.convertToBaseDto(e);
    }

    public AbstractPlatformService.SyncResult syncCampaigns(Long userId, Provider platform, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        SyncResult campaignResult = syncFromPlatform(user, platform, adAccountId);
        log.info("Synced {} campaigns (inserted: {}, updated: {})",
                campaignResult.fetched(),
                campaignResult.inserted(),
                campaignResult.updated());

        syncAdSetsForCampaigns(user, platform, adAccountId);

        return campaignResult;
    }

    /**
     * Syncs ad sets for all campaigns belonging to the user/platform/account.
     * This is called automatically after syncing campaigns to ensure child ad sets are populated.
     */
    @Transactional
    public void syncAdSetsForCampaigns(UserEntity user, Provider platform, String adAccountId) {
        try {
            log.info("Cascading sync: fetching ad sets for campaigns in account {}", adAccountId);

            AdSetStrategy adSetStrategy = adSetStrategyRegistry.of(platform);
            String accessToken = tokens.getAccessToken(user, platform);
            var client = clients.of(platform);

            String after = null;
            Map<String, String> baseQ = adSetStrategy.baseListQuery();
            List<AdSetDto> adSetDtos = new ArrayList<>();

            do {
                Map<String, String> q = new HashMap<>(baseQ);
                if (after != null) q.put("after", after);

                ResponseEntity<Map> resp = client.get(adSetStrategy.listPath(adAccountId), q, accessToken);
                Map<String, Object> body = resp.getBody();
                if (body == null) break;

                @SuppressWarnings("unchecked")
                var data = (List<Map<String, Object>>) body.get("data");
                if (data != null) {
                    for (var row : data) {
                        adSetDtos.add(adSetStrategy.mapGetRow(row, user.getId(), adAccountId));
                    }
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> paging = (Map<String, Object>) body.get("paging");
                @SuppressWarnings("unchecked")
                Map<String, Object> cursors = paging != null ? (Map<String, Object>) paging.get("cursors") : null;
                after = cursors != null ? (String) cursors.get("after") : null;

            } while (after != null);

            log.info("Fetched {} ad sets from platform", adSetDtos.size());

            List<String> externalIds = adSetDtos.stream()
                    .map(AdSetDto::getExternalId)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            if (externalIds.isEmpty()) {
                log.info("No ad sets to sync");
                return;
            }

            List<AdSetEntity> existing = adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
                    user, platform.name(), adAccountId, externalIds);
            Map<String, AdSetEntity> existingById = existing.stream()
                    .filter(e -> e.getExternalId() != null)
                    .collect(Collectors.toMap(AdSetEntity::getExternalId, Function.identity(), (a, b) -> a));

            int inserted = 0;

            for (AdSetDto dto : adSetDtos) {
                try {
                    String ext = dto.getExternalId();
                    if (ext == null || ext.isBlank()) continue;
                    ext = ext.trim();

                    if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                        rawDataCache.putAdSet(platform.name(), adAccountId, ext, dto.getRawData());
                    }

                    AdSetEntity adSetEntity = existingById.get(ext);
                    boolean needsSave = false;

                    if (adSetEntity == null) {
                        adSetEntity = new AdSetEntity();
                        adSetEntity.setUser(user);
                        adSetEntity.setPlatform(platform.name());
                        adSetEntity.setAdAccountId(adAccountId);
                        adSetEntity.setExternalId(ext);
                        adSetEntity.setCreatedAt(LocalDateTime.now());

                        adSetEntity.setName(dto.getName());
                        adSetEntity.setStatus(dto.getStatus());
                        adSetEntity.setCampaignExternalId(dto.getCampaignExternalId());
                        if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                            adSetEntity.setRawData(dto.getRawData());
                        }
                        adSetEntity.setUpdatedAt(LocalDateTime.now());

                        inserted++;
                        needsSave = true;
                    } else {
                        log.debug("Ad set {} already exists, updating data", ext);
                        adSetEntity.setName(dto.getName());
                        adSetEntity.setStatus(dto.getStatus());
                        if (dto.getRawData() != null && !dto.getRawData().isEmpty()) {
                            adSetEntity.setRawData(dto.getRawData());
                        }
                        adSetEntity.setUpdatedAt(LocalDateTime.now());
                        needsSave = true;
                    }

                    if (dto.getCampaignExternalId() != null) {
                        Optional<CampaignEntity> campaignOpt = campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalId(
                                user, platform.name(), adAccountId, dto.getCampaignExternalId());
                        if (campaignOpt.isPresent()) {
                            CampaignEntity campaign = campaignOpt.get();
                            adSetEntity.setCampaign(campaign);
                            adSetEntity.setCampaignExternalId(dto.getCampaignExternalId());
                            needsSave = true;
                        }
                    }

                    if (needsSave) {
                        adSetRepository.save(adSetEntity);
                    }

                } catch (Exception e) {
                    log.error("Failed to sync ad set {}: {}", dto.getExternalId(), e.getMessage());
                }
            }

            log.info("Completed cascading sync of ad sets (inserted: {}, skipped existing: {})", inserted, externalIds.size() - inserted);

        } catch (Exception e) {
            log.error("Failed to cascade sync ad sets: {}", e.getMessage(), e);
        }
    }

    /**
     * Get ad sets for a specific campaign, filtered by platform.
     */
    public List<AdSetDto> getAdSetsByCampaign(Long userId, Provider platform, Long campaignId) {
        userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> BusinessException.notFound("Campaign not found with id: " + campaignId));

        if (!campaign.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("Campaign with id " + campaignId + " does not belong to user " + userId);
        }
        if (!campaign.getPlatform().equals(platform.name())) {
            throw BusinessException.badRequest("Campaign platform '" + campaign.getPlatform()
                    + "' does not match requested platform '" + platform.name() + "'");
        }

        List<AdSetEntity> adSetEntities = adSetRepository.findByCampaignId(campaignId);
        return adSetEntities.stream()
                .map(this::toAdSetDtoWithAds)
                .collect(Collectors.toList());
    }

    @Transactional
    public int bulkUpdateStatus(Long userId, BulkStatusRequestDto req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            throw BusinessException.badRequest("ids list is required");
        }
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            throw BusinessException.badRequest("status is required");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        List<CampaignEntity> campaigns = campaignRepository.findAllById(req.getIds());
        int updated = 0;
        for (CampaignEntity c : campaigns) {
            if (!c.getUser().getId().equals(userId)) continue; // skip campaigns not owned by user
            c.setStatus(req.getStatus());
            c.setUpdatedAt(LocalDateTime.now());
            campaignRepository.save(c);
            try {
                if (c.getExternalId() != null) updateOnPlatform(c);
            } catch (Exception ex) {
                log.warn("Failed to sync bulk status update to platform for campaign {}: {}", c.getId(), ex.getMessage());
            }
            updated++;
        }
        return updated;
    }

    private AdSetDto toAdSetDtoWithAds(AdSetEntity adSet) {
        AdSetDto dto = adSetMapper.convertToBaseDto(adSet);
        List<com.example.marketing.ad.entity.AdEntity> adEntities = adRepository.findByAdSetId(adSet.getId());
        dto.setAds(adMapper.convertToBaseDto(adEntities));
        return dto;
    }
}
